package ma.dev.workflow.classification.classify;

import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

/**
 * The real classifier: Groq, through Spring AI's OpenAI client.
 *
 * <p>Groq speaks OpenAI's protocol, so no Groq-specific library exists and none is needed. Only
 * the base URL differs, and it lives in configuration.
 *
 * <p>The whole prompt is here rather than in a file. It is the most important behaviour in this
 * service and the thing most likely to need changing, so it belongs where it can be read next to
 * the code that sends it. The output shape is not written out at all: Spring AI derives the JSON
 * schema from {@link Suggestion} and its {@code @JsonPropertyDescription} annotations, which means
 * the record and the prompt cannot drift apart.
 */
@Component
@ConditionalOnProperty(name = "app.classification.provider", havingValue = "groq")
public class GroqClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(GroqClassifier.class);

    private static final String SYSTEM_PROMPT = """
            You are triaging tickets for a software team. Read the ticket and assess it.

            Rules:
            - Judge priority by user impact, not by how strongly the ticket is worded.
              An angry ticket about a typo is still LOW.
            - If the ticket does not say enough to decide a field, return null for that field
              rather than guessing.
            - Set confidence honestly. A low number is more useful than a wrong high one,
              because a high one applies the change without anyone checking it.
            - For missingInfo, list only facts a developer would actually need to start work.
              Do not pad the list.
            """;

    private final ChatClient chatClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final String model;

    public GroqClassifier(ChatClient chatClient,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                          @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.chatClient = chatClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.model = model;
    }

    @Override
    public Suggestion classify(IssueCreated issue) {
        // The breaker wraps the call, not the message handling. Once Groq has failed enough times,
        // further calls fail instantly instead of each one waiting for its own timeout - which is
        // what stops a Groq outage from tying up every listener thread this service has.
        return circuitBreakerFactory.create("groq").run(
                () -> callGroq(issue),
                this::translate);
    }

    @Override
    public String modelVersion() {
        return model;
    }

    private Suggestion callGroq(IssueCreated issue) {
        Suggestion suggestion = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text("""
                        Ticket {key}
                        Title: {title}
                        Description: {description}
                        """)
                        .param("key", issue.issueKey())
                        .param("title", nullSafe(issue.title()))
                        .param("description", nullSafe(issue.description())))
                .call()
                // Spring AI puts the schema in the request and maps the reply onto the record.
                // A model that answers with prose throws here, which is a permanent failure:
                // asking it again the same way gets the same prose.
                .entity(Suggestion.class);

        if (suggestion == null) {
            throw ClassificationFailedException.permanentFailure(
                    "The model returned nothing usable for " + issue.issueKey(), null);
        }
        return suggestion;
    }

    /**
     * Decides whether a failure is worth retrying. This is the input to the dead-letter decision.
     *
     * <p>The exception types are {@code com.openai.errors.*}, not Spring's
     * {@code HttpClientErrorException}. Spring AI 2.0 is built on the official openai-java SDK and
     * throws that SDK's exceptions. The first version of this method checked Spring's types, so
     * nothing ever matched and every failure fell through to "unexpected, retry" — including a
     * rejected API key, which was then retried three times per ticket rather than parked. The
     * classification looked right and did nothing.
     */
    private Suggestion translate(Throwable cause) {
        if (cause instanceof ClassificationFailedException failure) {
            throw failure;
        }

        // The breaker is open: Groq is failing and this call was not even attempted. Transient by
        // definition - the breaker half-opens on its own and the message should still be waiting.
        if (cause instanceof CallNotPermittedException) {
            throw ClassificationFailedException.transientFailure(
                    "The Groq circuit breaker is open.", cause);
        }

        // Rate limited. The most ordinary reason to try again in a moment.
        if (cause instanceof RateLimitException) {
            throw ClassificationFailedException.transientFailure("Groq rate limited the request.", cause);
        }

        // A missing or wrong key. Retrying is pure delay: it will be just as wrong in two seconds.
        // Parking it is what lets the work be replayed once somebody fixes the configuration.
        if (cause instanceof UnauthorizedException || cause instanceof PermissionDeniedException) {
            log.error("Groq rejected the API key. Set GROQ_API_KEY, then replay the dead letters.");
            throw ClassificationFailedException.permanentFailure("Groq rejected the API key.", cause);
        }

        // Requests this service built wrongly: a bad model name, a URL with no /v1, a payload Groq
        // will not accept. Sending the same thing again cannot produce a different answer.
        if (cause instanceof NotFoundException
                || cause instanceof BadRequestException
                || cause instanceof UnprocessableEntityException) {
            log.error("Groq refused the request itself, so this is configuration and not weather: {}",
                    cause.getMessage());
            throw ClassificationFailedException.permanentFailure(
                    "Groq refused the request: " + cause.getMessage(), cause);
        }

        // Groq's problem, or the network's. Both usually pass.
        if (cause instanceof InternalServerException || cause instanceof OpenAIIoException) {
            throw ClassificationFailedException.transientFailure(
                    "Groq is unavailable: " + cause.getMessage(), cause);
        }

        // The model answered something that is not the JSON the schema asked for. Asking again the
        // same way gets the same prose.
        if (cause instanceof OpenAIInvalidDataException) {
            throw ClassificationFailedException.permanentFailure(
                    "Groq returned something that is not the requested JSON.", cause);
        }

        // Unrecognised. Treated as transient so a ticket is never discarded because of a failure
        // mode nobody anticipated - the retry limit still stops it looping forever.
        throw ClassificationFailedException.transientFailure(
                "Unexpected failure calling Groq: " + cause.getMessage(), cause);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "(none given)" : value;
    }
}
