package ma.dev.workflow.classification.classify;

import jakarta.annotation.PostConstruct;
import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keyword rules standing in for a model, so the pipeline can be built and shown without an API key.
 *
 * <p>It is not pretending to be clever and it must never be mistaken for the real thing. Three
 * things make it impossible to demonstrate this by accident:
 *
 * <ol>
 *   <li>a warning on startup that says what it is</li>
 *   <li>{@code modelVersion} is {@code stub-v1}, stored on every row it produces</li>
 *   <li>the suggestion chip in the UI prints that version, so the screen itself says so</li>
 * </ol>
 *
 * <p>Its confidence is deliberately kept below the 0.85 auto-apply threshold. Keyword matching has
 * not earned the right to change a ticket unattended, and a stub that silently rewrote issues
 * would be worse than no stub at all.
 */
@Component
@ConditionalOnProperty(name = "app.classification.provider", havingValue = "stub", matchIfMissing = true)
public class StubClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(StubClassifier.class);

    /** Below the auto-apply threshold on purpose. A guess proposes; it does not decide. */
    private static final float STUB_CONFIDENCE = 0.55f;

    private static final List<String> BUG_WORDS =
            List.of("bug", "error", "crash", "broken", "fail", "exception", "500", "wrong");
    private static final List<String> FEATURE_WORDS =
            List.of("add", "feature", "support for", "would like", "new ", "implement", "allow");
    private static final List<String> SUPPORT_WORDS =
            List.of("how do i", "question", "help", "cannot find", "where is");
    private static final List<String> URGENT_WORDS =
            List.of("urgent", "critical", "asap", "production", "outage", "down", "blocker",
                    "data loss", "everyone");
    private static final List<String> ANGRY_WORDS =
            List.of("unacceptable", "ridiculous", "again", "still broken", "frustrat", "terrible");

    @PostConstruct
    void announce() {
        log.warn("""

                ============================================================
                 Classification provider is STUB.
                 Suggestions come from keyword rules, not from a model.
                 Every suggestion is stored as modelVersion=stub-v1.
                 To use the real thing: set GROQ_API_KEY, then
                 app.classification.provider=groq
                ============================================================""");
    }

    @Override
    public Suggestion classify(IssueCreated issue) {
        String text = ((issue.title() == null ? "" : issue.title()) + " "
                + (issue.description() == null ? "" : issue.description()))
                .toLowerCase(Locale.ROOT);

        return new Suggestion(
                guessType(text),
                guessPriority(text),
                guessTeam(text),
                guessEffort(text),
                guessSentiment(text),
                STUB_CONFIDENCE,
                findMissingInfo(text));
    }

    @Override
    public String modelVersion() {
        return "stub-v1";
    }

    private String guessType(String text) {
        if (containsAny(text, BUG_WORDS)) return "BUG";
        if (containsAny(text, FEATURE_WORDS)) return "FEATURE";
        if (containsAny(text, SUPPORT_WORDS)) return "SUPPORT";
        return "TASK";
    }

    private String guessPriority(String text) {
        if (containsAny(text, URGENT_WORDS)) return "CRITICAL";
        if (containsAny(text, BUG_WORDS)) return "HIGH";
        return "MEDIUM";
    }

    private String guessTeam(String text) {
        if (containsAny(text, List.of("css", "button", "screen", "page", "layout", "ui", "browser"))) {
            return "Frontend";
        }
        if (containsAny(text, List.of("api", "endpoint", "database", "query", "sql", "500", "server"))) {
            return "Backend";
        }
        if (containsAny(text, List.of("deploy", "docker", "pipeline", "server down", "memory"))) {
            return "Infrastructure";
        }
        return null;
    }

    private String guessEffort(String text) {
        if (text.length() > 400) return "LARGE";
        if (text.length() > 120) return "MEDIUM";
        return "SMALL";
    }

    private Float guessSentiment(String text) {
        if (containsAny(text, ANGRY_WORDS)) return -0.6f;
        if (containsAny(text, List.of("thanks", "great", "please", "appreciate"))) return 0.4f;
        return 0.0f;
    }

    /**
     * The "missing information" half of the AI layer, done with the bluntest possible test: a bug
     * report with no steps and no expectation is the single most common way a ticket wastes a
     * developer's afternoon.
     */
    private List<String> findMissingInfo(String text) {
        List<String> missing = new ArrayList<>();
        if (text.length() < 40) {
            missing.add("the ticket is very short - what actually happened?");
        }
        if (containsAny(text, BUG_WORDS)
                && !containsAny(text, List.of("step", "reproduce", "when i", "after i"))) {
            missing.add("no steps to reproduce");
        }
        if (containsAny(text, List.of("browser", "page", "screen"))
                && !containsAny(text, List.of("chrome", "firefox", "safari", "edge", "version"))) {
            missing.add("no browser or version");
        }
        return missing;
    }

    private boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }
}
