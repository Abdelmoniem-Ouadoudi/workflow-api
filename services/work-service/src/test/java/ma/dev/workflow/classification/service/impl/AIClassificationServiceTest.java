package ma.dev.workflow.classification.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.classification.dto.mapper.AIClassificationMapper;
import ma.dev.workflow.classification.models.AIClassification;
import ma.dev.workflow.classification.models.enums.Effort;
import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.classification.repositories.AIClassificationRepository;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.issue.events.IssueClassifiedEvent;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.IssueType;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.repositories.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens to a suggestion when it arrives.
 *
 * <p>Every rule here is a judgement call rather than a fact — when to trust a model enough to
 * change a ticket unattended, what to do with a value it invented, what a second delivery of the
 * same message means. None of them are visible in the schema, and all of them would fail quietly.
 */
class AIClassificationServiceTest {

    private static final float THRESHOLD = 0.85f;

    private AIClassificationRepository classificationRepository;
    private IssueRepository issueRepository;
    private AIClassificationService service;

    private Issue issue;

    @BeforeEach
    void setUp() {
        classificationRepository = mock(AIClassificationRepository.class);
        issueRepository = mock(IssueRepository.class);
        AIClassificationMapper mapper = mock(AIClassificationMapper.class);

        service = new AIClassificationService(classificationRepository, issueRepository, mapper, THRESHOLD);

        issue = new Issue();
        issue.setId(7L);
        issue.setIssueKey("TEST-7");
        issue.setType(IssueType.TASK);
        issue.setPriority(Priority.LOW);
        issue.setStatus(Status.TO_DO);

        when(issueRepository.findById(7L)).thenReturn(Optional.of(issue));
        when(classificationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(issueRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("the auto-apply threshold")
    class AutoApply {

        @Test
        @DisplayName("a confident suggestion is written onto the issue with nobody watching")
        void appliesAboveTheThreshold() {
            service.record(event(0.95f, "BUG", "CRITICAL"));

            assertThat(issue.getType()).isEqualTo(IssueType.BUG);
            assertThat(issue.getPriority()).isEqualTo(Priority.CRITICAL);
            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.AUTO_APPLIED);
        }

        @Test
        @DisplayName("an unsure suggestion is stored and waits for a person")
        void waitsBelowTheThreshold() {
            service.record(event(0.55f, "BUG", "CRITICAL"));

            assertThat(issue.getType()).isEqualTo(IssueType.TASK);
            assertThat(issue.getPriority()).isEqualTo(Priority.LOW);
            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
            verify(issueRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("exactly at the threshold counts as confident")
        void appliesAtTheThresholdItself() {
            service.record(event(THRESHOLD, "BUG", null));

            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.AUTO_APPLIED);
        }

        @Test
        @DisplayName("confidence is clamped, so a model returning 1.4 cannot force its way through")
        void clampsConfidenceIntoRange() {
            service.record(event(1.4f, "BUG", "HIGH"));

            assertThat(saved().getConfidence()).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("a missing confidence is read as zero, not as certainty")
        void treatsMissingConfidenceAsZero() {
            service.record(event(null, "BUG", "HIGH"));

            assertThat(saved().getConfidence()).isEqualTo(0.0f);
            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        }

        @Test
        @DisplayName("confident but with no opinion is not applied - there is nothing to apply")
        void doesNotAutoApplyAnEmptySuggestion() {
            service.record(event(0.99f, null, null));

            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
            verify(issueRepository, never()).saveAndFlush(any());
        }

        /**
         * Status is never suggested and never applied. An issue is born TO_DO and only the
         * transition map moves it; letting a model skip that would make the workflow negotiable.
         */
        @Test
        @DisplayName("the status is never touched, however confident the model is")
        void neverChangesStatus() {
            service.record(event(1.0f, "BUG", "CRITICAL"));

            assertThat(issue.getStatus()).isEqualTo(Status.TO_DO);
        }
    }

    @Nested
    @DisplayName("messages arriving more than once")
    class Idempotency {

        /**
         * RabbitMQ delivers at least once. Without the lookup-then-update, a redelivery would hit
         * the unique constraint on issue_id and land in the dead-letter queue for no reason.
         */
        @Test
        @DisplayName("a redelivery updates the existing row instead of inserting a second")
        void updatesRatherThanInserting() {
            AIClassification existing = new AIClassification();
            existing.setId(99L);
            existing.setIssue(issue);
            existing.setReviewStatus(ReviewStatus.PENDING);
            when(classificationRepository.findByIssueId(7L)).thenReturn(Optional.of(existing));

            service.record(event(0.95f, "BUG", "CRITICAL"));

            assertThat(saved().getId()).isEqualTo(99L);
        }
    }

    @Nested
    @DisplayName("values the model invented")
    class LenientParsing {

        /**
         * The producer is another service. One unrecognised value should cost that field, not the
         * whole classification - a rejected message means the issue shows nothing at all.
         */
        @Test
        @DisplayName("an unknown type is dropped, and the rest of the suggestion survives")
        void ignoresAnUnknownEnumValue() {
            service.record(new IssueClassifiedEvent(7L, "WIZARDRY", "HIGH", "Backend",
                    "ENORMOUS", 0.1f, 0.5f, List.of(), "test-model"));

            assertThat(saved().getSuggestedType()).isNull();
            assertThat(saved().getEffortHint()).isNull();
            assertThat(saved().getSuggestedPriority()).isEqualTo(Priority.HIGH);
            assertThat(saved().getSuggestedTeam()).isEqualTo("Backend");
        }

        @Test
        @DisplayName("lower case and stray spaces are accepted")
        void normalisesCaseAndWhitespace() {
            service.record(new IssueClassifiedEvent(7L, " bug ", "critical", null,
                    "small", null, 0.5f, List.of(), "test-model"));

            assertThat(saved().getSuggestedType()).isEqualTo(IssueType.BUG);
            assertThat(saved().getSuggestedPriority()).isEqualTo(Priority.CRITICAL);
            assertThat(saved().getEffortHint()).isEqualTo(Effort.SMALL);
        }
    }

    @Nested
    @DisplayName("a person deciding")
    class Review {

        @Test
        @DisplayName("accepting writes the suggested values onto the issue")
        void acceptApplies() {
            when(classificationRepository.findByIssueId(7L))
                    .thenReturn(Optional.of(pending(IssueType.BUG, Priority.CRITICAL)));

            service.accept(7L);

            assertThat(issue.getType()).isEqualTo(IssueType.BUG);
            assertThat(issue.getPriority()).isEqualTo(Priority.CRITICAL);
            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.CONFIRMED);
        }

        @Test
        @DisplayName("overriding records the disagreement and leaves the issue alone")
        void overrideRecordsButChangesNothing() {
            when(classificationRepository.findByIssueId(7L))
                    .thenReturn(Optional.of(pending(IssueType.BUG, Priority.CRITICAL)));

            service.override(7L);

            assertThat(issue.getType()).isEqualTo(IssueType.TASK);
            assertThat(issue.getPriority()).isEqualTo(Priority.LOW);
            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.OVERRIDDEN);
            verify(issueRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("a decision is made once")
        void refusesASecondDecision() {
            AIClassification decided = pending(IssueType.BUG, Priority.HIGH);
            decided.setReviewStatus(ReviewStatus.CONFIRMED);
            when(classificationRepository.findByIssueId(7L)).thenReturn(Optional.of(decided));

            assertThatThrownBy(() -> service.accept(7L))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getCode())
                    .isEqualTo("ALREADY_REVIEWED");
        }

        /**
         * An auto-applied suggestion has not been reviewed by anyone, so a person can still
         * confirm it. That is what turns an assumption into a judgement, and what makes the
         * agreement rate mean something.
         */
        @Test
        @DisplayName("an auto-applied suggestion can still be confirmed by a person")
        void allowsConfirmingAnAutoAppliedSuggestion() {
            AIClassification applied = pending(IssueType.BUG, Priority.HIGH);
            applied.setReviewStatus(ReviewStatus.AUTO_APPLIED);
            when(classificationRepository.findByIssueId(7L)).thenReturn(Optional.of(applied));

            service.accept(7L);

            assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.CONFIRMED);
        }

        @Test
        @DisplayName("accepting a suggestion that has not arrived is a 404, not a crash")
        void refusesWhenThereIsNoSuggestionYet() {
            when(classificationRepository.findByIssueId(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.accept(7L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    private AIClassification pending(IssueType type, Priority priority) {
        AIClassification classification = new AIClassification();
        classification.setIssue(issue);
        classification.setSuggestedType(type);
        classification.setSuggestedPriority(priority);
        classification.setReviewStatus(ReviewStatus.PENDING);
        return classification;
    }

    private IssueClassifiedEvent event(Float confidence, String type, String priority) {
        return new IssueClassifiedEvent(7L, type, priority, "Backend", "SMALL",
                0.0f, confidence, List.of(), "test-model");
    }

    /** The row as it was handed to the repository — what would actually have been written. */
    private AIClassification saved() {
        org.mockito.ArgumentCaptor<AIClassification> captor =
                org.mockito.ArgumentCaptor.forClass(AIClassification.class);
        verify(classificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
