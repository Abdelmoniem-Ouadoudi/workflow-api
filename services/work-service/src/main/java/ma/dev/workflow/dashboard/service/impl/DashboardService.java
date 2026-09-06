package ma.dev.workflow.dashboard.service.impl;

import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.dashboard.dto.CountByLabel;
import ma.dev.workflow.dashboard.dto.DashboardDTO;
import ma.dev.workflow.dashboard.repositories.DashboardRepository;
import ma.dev.workflow.dashboard.service.IDashboardService;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.repositories.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService implements IDashboardService {

    private final DashboardRepository dashboardRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccess projectAccess;

    public DashboardService(DashboardRepository dashboardRepository,
                            ProjectRepository projectRepository,
                            ProjectAccess projectAccess) {
        this.dashboardRepository = dashboardRepository;
        this.projectRepository = projectRepository;
        this.projectAccess = projectAccess;
    }

    /**
     * The same numbers as before, over the projects the caller is on.
     *
     * <p>This is what BACKLOG item 36 was waiting for. The question it parked on was "type
     * distribution across a team means something different from type distribution in one project,
     * so which is this screen for" — and membership settles it without a query parameter: the
     * screen is for your work. An administrator, who is on no project but may see all of them, gets
     * the system-wide view that everybody used to get.
     */
    @Override
    public DashboardDTO load() {
        List<Long> projectIds = projectAccess.isAdmin()
                ? projectRepository.findAllIds()
                : projectAccess.myProjectIds();

        // Nothing to count, and asking anyway would send "in ()" to the database. A person on no
        // projects sees an honest empty dashboard rather than an error.
        if (projectIds.isEmpty()) {
            return empty();
        }

        Map<String, Long> reviews = dashboardRepository.countByReviewStatus(projectIds).stream()
                .collect(Collectors.toMap(CountByLabel::label, CountByLabel::count,
                        Long::sum));

        long autoApplied = countOf(reviews, ReviewStatus.AUTO_APPLIED);
        long confirmed = countOf(reviews, ReviewStatus.CONFIRMED);
        long overridden = countOf(reviews, ReviewStatus.OVERRIDDEN);
        long pending = countOf(reviews, ReviewStatus.PENDING);

        return new DashboardDTO(
                dashboardRepository.countIssues(projectIds),
                dashboardRepository.countClassifications(projectIds),
                pending,
                agreementRate(autoApplied, confirmed, overridden),
                dashboardRepository.countByType(projectIds),
                dashboardRepository.countByPriority(projectIds),
                dashboardRepository.countByStatus(projectIds),
                dashboardRepository.countByTeam(projectIds),
                dashboardRepository.countByEffort(projectIds));
    }

    /**
     * Zero issues, and a null agreement rate.
     *
     * <p>Null rather than zero for the same reason as {@link #agreementRate}: with nothing judged,
     * "0%" would claim the model is always wrong.
     */
    private DashboardDTO empty() {
        return new DashboardDTO(0L, 0L, 0L, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * The number the whole AI layer is judged by.
     *
     * <p>{@code (AUTO_APPLIED + CONFIRMED) / (AUTO_APPLIED + CONFIRMED + OVERRIDDEN)}.
     *
     * <p>Two decisions are worth defending. **PENDING is excluded**: a suggestion nobody has looked
     * at is not a disagreement, and including it would make this number fall every time a ticket is
     * filed — it would measure how busy the team is, not how right the model is.
     *
     * <p>**AUTO_APPLIED counts as agreement.** It is the weakest part of the number, because nobody
     * confirmed it: it means the model was confident and was not contradicted. Silence is being read
     * as assent. The honest reading is "not overridden" rather than "verified correct", and that is
     * worth saying out loud rather than letting the percentage imply more than it knows.
     *
     * <p>Null, not zero, when nothing has been judged. Zero would read as "always wrong", which is a
     * very different claim from "nobody has checked yet".
     */
    private Double agreementRate(long autoApplied, long confirmed, long overridden) {
        long judged = autoApplied + confirmed + overridden;
        if (judged == 0) {
            return null;
        }
        return Math.round((double) (autoApplied + confirmed) / judged * 1000) / 10.0;
    }

    private long countOf(Map<String, Long> counts, ReviewStatus status) {
        return counts.getOrDefault(status.name(), 0L);
    }
}
