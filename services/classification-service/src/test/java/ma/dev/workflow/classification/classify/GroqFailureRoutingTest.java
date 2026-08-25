package ma.dev.workflow.classification.classify;

import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which failures are retried, and which are parked.
 *
 * <p>This is the dead-letter queue's brain, and it is worth more than one test because it was
 * already wrong once. The first version checked Spring's {@code HttpClientErrorException}, but
 * Spring AI 2.0 is built on the official openai-java SDK and throws {@code com.openai.errors.*} —
 * so nothing matched, every failure fell through to "unexpected, retry", and a rejected API key
 * was retried three times per ticket instead of being parked. The classification looked correct
 * and did nothing.
 *
 * <p>These tests name the SDK's exception types explicitly, so the same silent mismatch would fail
 * a build rather than a defence.
 */
class GroqFailureRoutingTest {

    private CircuitBreaker breaker;
    private GroqClassifier classifier;

    @BeforeEach
    void setUp() {
        breaker = mock(CircuitBreaker.class);
        CircuitBreakerFactory<?, ?> factory = mock(CircuitBreakerFactory.class);
        when(factory.create("groq")).thenReturn(breaker);

        classifier = new GroqClassifier(mock(ChatClient.class), factory, "test-model");
    }

    @Test
    @DisplayName("a rate limit is transient: it will probably work in a second")
    void rateLimitIsTransient() {
        assertThat(failureFor(mock(RateLimitException.class)).isPermanent()).isFalse();
    }

    @Test
    @DisplayName("a 5xx from the provider is transient")
    void serverErrorIsTransient() {
        assertThat(failureFor(mock(InternalServerException.class)).isPermanent()).isFalse();
    }

    @Test
    @DisplayName("a network failure is transient")
    void networkFailureIsTransient() {
        assertThat(failureFor(mock(OpenAIIoException.class)).isPermanent()).isFalse();
    }

    /**
     * The breaker being open means Groq is failing and this call was not even attempted. Transient
     * by definition: the breaker half-opens on its own and the message should still be waiting.
     */
    @Test
    @DisplayName("an open circuit breaker is transient, not a reason to discard the ticket")
    void openBreakerIsTransient() {
        assertThat(failureFor(mock(CallNotPermittedException.class)).isPermanent()).isFalse();
    }

    /**
     * The one that was broken. A wrong key will be just as wrong in two seconds, so retrying is
     * pure delay — and with three retries per ticket it is three wasted calls each.
     */
    @Test
    @DisplayName("a rejected API key is permanent: it will not fix itself")
    void unauthorizedIsPermanent() {
        assertThat(failureFor(mock(UnauthorizedException.class)).isPermanent()).isTrue();
    }

    @Test
    @DisplayName("a forbidden key is permanent")
    void forbiddenIsPermanent() {
        assertThat(failureFor(mock(PermissionDeniedException.class)).isPermanent()).isTrue();
    }

    /**
     * This is what a base URL missing its /v1 produces. Retrying a request this service built
     * wrongly cannot produce a different answer.
     */
    @Test
    @DisplayName("a 404 is permanent - it means the URL is wrong, which is configuration")
    void notFoundIsPermanent() {
        assertThat(failureFor(mock(NotFoundException.class)).isPermanent()).isTrue();
    }

    @Test
    @DisplayName("a malformed request is permanent")
    void badRequestIsPermanent() {
        assertThat(failureFor(mock(BadRequestException.class)).isPermanent()).isTrue();
    }

    /**
     * A failure mode nobody anticipated should cost a retry, not a ticket. The retry limit still
     * stops it looping forever, so the worst case is a short delay before it is parked anyway.
     */
    @Test
    @DisplayName("an unrecognised failure is treated as transient, so nothing is discarded blindly")
    void unknownFailureIsTransient() {
        assertThat(failureFor(new IllegalStateException("something new")).isPermanent()).isFalse();
    }

    @Test
    @DisplayName("a decision already made is passed through rather than re-judged")
    void passesThroughAnAlreadyClassifiedFailure() {
        ClassificationFailedException original =
                ClassificationFailedException.permanentFailure("already decided", null);

        assertThat(failureFor(original)).isSameAs(original);
    }

    @Test
    @DisplayName("the model version travels with the answer, so a bad run can be traced to it")
    void reportsItsModelVersion() {
        assertThat(classifier.modelVersion()).isEqualTo("test-model");
    }

    /**
     * Drives the real fallback. The breaker is stubbed to do what it does when a call fails: hand
     * the throwable to the fallback function, which is the method under test.
     */
    @SuppressWarnings("unchecked")
    private ClassificationFailedException failureFor(Throwable cause) {
        when(breaker.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(call -> {
                    Function<Throwable, Suggestion> fallback = call.getArgument(1);
                    return fallback.apply(cause);
                });

        IssueCreated ticket = new IssueCreated(1L, "TEST-1", "A title", "A description", "TEST");

        return (ClassificationFailedException) org.assertj.core.api.Assertions
                .catchThrowable(() -> classifier.classify(ticket));
    }

    @Test
    @DisplayName("every failure surfaces as a ClassificationFailedException, never a raw SDK error")
    void alwaysWrapsTheCause() {
        assertThatThrownBy(() -> {
            throw failureFor(mock(RateLimitException.class));
        }).isInstanceOf(ClassificationFailedException.class);
    }
}
