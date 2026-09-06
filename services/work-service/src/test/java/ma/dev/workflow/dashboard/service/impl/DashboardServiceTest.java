package ma.dev.workflow.dashboard.service.impl;

import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.dashboard.dto.CountByLabel;
import ma.dev.workflow.dashboard.dto.DashboardDTO;
import ma.dev.workflow.dashboard.repositories.DashboardRepository;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The AI agreement rate.
 *
 * <p>This number is what the whole AI layer is judged by, and it is one arithmetic slip away from
 * being a lie. The rule it encodes — that an undecided suggestion is not a disagreement — is a
 * judgement, not a formula, and nothing about the schema records it.
 */
class DashboardServiceTest {

    /** The projects the caller can see. Which ones they are does not matter to the arithmetic. */
    private static final List<Long> PROJECTS = List.of(1L);

    private DashboardRepository repository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(DashboardRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        // Not an admin, and on one project. Scoping is tested by ProjectAccessTest; what is under
        // test here is the agreement rate, so this only has to be non-empty.
        ProjectAccess projectAccess = mock(ProjectAccess.class);
        when(projectAccess.isAdmin()).thenReturn(false);
        when(projectAccess.myProjectIds()).thenReturn(PROJECTS);

        service = new DashboardService(repository, projectRepository, projectAccess);

        // The distributions are plain group-bys and are not what this test is about.
        when(repository.countByType(PROJECTS)).thenReturn(List.of());
        when(repository.countByPriority(PROJECTS)).thenReturn(List.of());
        when(repository.countByStatus(PROJECTS)).thenReturn(List.of());
        when(repository.countByTeam(PROJECTS)).thenReturn(List.of());
        when(repository.countByEffort(PROJECTS)).thenReturn(List.of());
    }

    @Test
    @DisplayName("agreement is applied plus accepted, over everything that was decided")
    void computesTheRateFromDecidedSuggestions() {
        givenReviews(3, 1, 1, 0);  // 3 auto-applied, 1 confirmed, 1 overridden

        assertThat(service.load().aiAgreementRate()).isEqualTo(80.0);
    }

    /**
     * The rule that matters most. Counting PENDING as disagreement would make this number fall
     * every time somebody files a ticket — it would measure how busy the team is, not how right
     * the model is.
     */
    @Test
    @DisplayName("suggestions nobody has looked at do not count against the model")
    void ignoresPendingSuggestions() {
        givenReviews(1, 1, 0, 500);

        assertThat(service.load().aiAgreementRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("every suggestion overridden is nought percent, not null")
    void reportsZeroWhenEverythingWasRejected() {
        givenReviews(0, 0, 4, 2);

        assertThat(service.load().aiAgreementRate()).isEqualTo(0.0);
    }

    /**
     * Null and zero are different claims. Zero says the model is always wrong; null says nobody
     * has checked. On a fresh install the second is true and the first would be a slander.
     */
    @Test
    @DisplayName("nothing judged yet is null, which is not the same as nought percent")
    void reportsNullWhenNothingHasBeenJudged() {
        givenReviews(0, 0, 0, 12);

        assertThat(service.load().aiAgreementRate()).isNull();
    }

    @Test
    @DisplayName("an empty system reports null rather than dividing by zero")
    void survivesWithNoClassificationsAtAll() {
        givenReviews(0, 0, 0, 0);

        DashboardDTO dashboard = service.load();

        assertThat(dashboard.aiAgreementRate()).isNull();
        assertThat(dashboard.awaitingReview()).isZero();
    }

    @Test
    @DisplayName("the rate is rounded to one decimal, not left as a recurring number")
    void roundsToOneDecimalPlace() {
        givenReviews(1, 0, 2, 0);  // 1/3

        assertThat(service.load().aiAgreementRate()).isEqualTo(33.3);
    }

    @Test
    @DisplayName("awaiting review is the pending count, which is the queue of human work")
    void reportsWhatIsWaitingForAPerson() {
        givenReviews(2, 2, 2, 9);

        assertThat(service.load().awaitingReview()).isEqualTo(9);
    }

    private void givenReviews(long autoApplied, long confirmed, long overridden, long pending) {
        when(repository.countByReviewStatus(PROJECTS)).thenReturn(List.of(
                new CountByLabel(ReviewStatus.AUTO_APPLIED.name(), autoApplied),
                new CountByLabel(ReviewStatus.CONFIRMED.name(), confirmed),
                new CountByLabel(ReviewStatus.OVERRIDDEN.name(), overridden),
                new CountByLabel(ReviewStatus.PENDING.name(), pending)));
        when(repository.countClassifications(PROJECTS))
                .thenReturn(autoApplied + confirmed + overridden + pending);
    }
}
