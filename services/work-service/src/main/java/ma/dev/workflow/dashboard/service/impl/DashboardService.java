package ma.dev.workflow.dashboard.service.impl;

import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.dashboard.dto.CountByLabel;
import ma.dev.workflow.dashboard.dto.DashboardDTO;
import ma.dev.workflow.dashboard.repositories.DashboardRepository;
import ma.dev.workflow.dashboard.service.IDashboardService;
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

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Override
    public DashboardDTO load() {
        List<CountByLabel> byReviewStatus = dashboardRepository.countByReviewStatus();
        Map<String, Long> reviews = byReviewStatus.stream()
                .collect(Collectors.toMap(CountByLabel::label, CountByLabel::count,
                        Long::sum));

        long autoApplied = countOf(reviews, ReviewStatus.AUTO_APPLIED);
        long confirmed = countOf(reviews, ReviewStatus.CONFIRMED);
        long overridden = countOf(reviews, ReviewStatus.OVERRIDDEN);
        long pending = countOf(reviews, ReviewStatus.PENDING);

        return new DashboardDTO(
                dashboardRepository.count(),
                dashboardRepository.countClassifications(),
                pending,
                agreementRate(autoApplied, confirmed, overridden),
                dashboardRepository.countByType(),
                dashboardRepository.countByPriority(),
                dashboardRepository.countByStatus(),
                dashboardRepository.countByTeam(),
                dashboardRepository.countByEffort());
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
