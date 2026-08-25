package ma.dev.workflow.classification.classify;

import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;

/**
 * Reads a ticket and says what it thinks it is.
 *
 * <p>Two implementations, chosen by the {@code app.classification.provider} property: the real one
 * calls Groq, and the stub applies keyword rules so the whole pipeline — queue, retry, dead-letter
 * queue, persistence, UI — can be built and demonstrated on a machine with no API key.
 *
 * <p>The interface exists for that reason and not for testing theatre. It is also the seam that
 * makes swapping model providers a configuration change: nothing outside these two classes knows
 * that Groq is involved.
 */
public interface Classifier {

    /**
     * @return the assessment, never null
     * @throws ClassificationFailedException the model could not be reached, or answered something
     *                                       unusable. The listener decides from that whether to
     *                                       retry or to park the message.
     */
    Suggestion classify(IssueCreated issue);

    /**
     * Which model produced these answers, stored with every row.
     *
     * <p>Part of the interface rather than a detail, because an answer whose author is unknown
     * cannot be audited later — and because it is what puts {@code stub-v1} on screen instead of
     * letting keyword matching look like intelligence.
     */
    String modelVersion();
}
