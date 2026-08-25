package ma.dev.workflow.classification.classify;

import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keyword classifier that stands in when there is no API key.
 *
 * <p>Tested for one reason above all: it must never be mistaken for the real thing. Its confidence
 * has to stay below the auto-apply threshold and its model version has to say `stub-v1`, because
 * those two facts are what stop keyword matching from quietly rewriting tickets and being
 * demonstrated as intelligence.
 */
class StubClassifierTest {

    /** Must stay below work-service's app.classification.auto-apply-threshold, which is 0.85. */
    private static final float AUTO_APPLY_THRESHOLD = 0.85f;

    private final StubClassifier classifier = new StubClassifier();

    @Test
    @DisplayName("it never sounds confident enough to change a ticket unattended")
    void staysBelowTheAutoApplyThreshold() {
        Suggestion suggestion = classify("Everything is broken", "urgent production outage");

        assertThat(suggestion.confidence()).isLessThan(AUTO_APPLY_THRESHOLD);
    }

    @Test
    @DisplayName("it says which model it is, and the answer is not a model")
    void identifiesItselfAsAStub() {
        assertThat(classifier.modelVersion()).isEqualTo("stub-v1");
    }

    @Test
    @DisplayName("crash words read as a bug")
    void readsCrashWordsAsABug() {
        assertThat(classify("Login page crashes with a 500 error", "").type()).isEqualTo("BUG");
    }

    @Test
    @DisplayName("request words read as a feature")
    void readsRequestWordsAsAFeature() {
        assertThat(classify("Add CSV export to the reports page", "").type()).isEqualTo("FEATURE");
    }

    @Test
    @DisplayName("a question reads as support")
    void readsAQuestionAsSupport() {
        assertThat(classify("How do I reset my password", "").type()).isEqualTo("SUPPORT");
    }

    @Test
    @DisplayName("anything it cannot place is a task, not a guess at something else")
    void fallsBackToTask() {
        assertThat(classify("Quarterly planning session notes", "").type()).isEqualTo("TASK");
    }

    @Test
    @DisplayName("urgency words raise the priority")
    void readsUrgencyAsCritical() {
        assertThat(classify("Production is down for everyone", "").priority()).isEqualTo("CRITICAL");
    }

    /**
     * The missing-information half of the AI layer. A bug report with no steps is the single most
     * common way a ticket wastes a developer's afternoon.
     */
    @Test
    @DisplayName("a bug with no steps to reproduce is flagged as incomplete")
    void noticesAMissingReproduction() {
        // Deliberately avoids "when i", "after i", "step" and "reproduce" - those are exactly the
        // phrases the stub reads as an attempt to describe how to trigger the fault, so including
        // one would be testing the opposite of what this test claims.
        Suggestion suggestion = classify("The export button is broken and throws an error",
                "It fails every single time for all of the users in our team without exception.");

        assertThat(suggestion.missingInfo()).contains("no steps to reproduce");
    }

    @Test
    @DisplayName("a bug that does say how to trigger it is not asked for steps")
    void staysQuietWhenTheStepsAreThere() {
        Suggestion suggestion = classify("The export button is broken and throws an error",
                "Steps to reproduce: open the export dialog and press the confirm control.");

        assertThat(suggestion.missingInfo()).doesNotContain("no steps to reproduce");
    }

    @Test
    @DisplayName("a ticket that says almost nothing is flagged for being too short")
    void noticesAnEmptyTicket() {
        assertThat(classify("Broken", "").missingInfo())
                .anyMatch(note -> note.contains("very short"));
    }

    @Test
    @DisplayName("a complete ticket is not nagged about nothing")
    void staysQuietWhenTheTicketIsComplete() {
        Suggestion suggestion = classify(
                "Quarterly planning session notes for the coming release cycle",
                "Notes from the planning meeting, including the agreed scope and the owners.");

        assertThat(suggestion.missingInfo()).isEmpty();
    }

    @Test
    @DisplayName("a null description does not blow up")
    void survivesAMissingDescription() {
        Suggestion suggestion = classifier.classify(
                new IssueCreated(1L, "TEST-1", "Something is broken here", null, "TEST"));

        assertThat(suggestion.type()).isEqualTo("BUG");
    }

    @Test
    @DisplayName("a null title does not blow up either")
    void survivesAMissingTitle() {
        Suggestion suggestion = classifier.classify(
                new IssueCreated(1L, "TEST-1", null, null, "TEST"));

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.confidence()).isLessThan(AUTO_APPLY_THRESHOLD);
    }

    private Suggestion classify(String title, String description) {
        return classifier.classify(new IssueCreated(1L, "TEST-1", title, description, "TEST"));
    }
}
