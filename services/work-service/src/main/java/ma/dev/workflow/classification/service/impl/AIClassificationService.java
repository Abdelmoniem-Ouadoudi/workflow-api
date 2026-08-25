package ma.dev.workflow.classification.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.classification.dto.AIClassificationDTO;
import ma.dev.workflow.classification.dto.mapper.AIClassificationMapper;
import ma.dev.workflow.classification.models.AIClassification;
import ma.dev.workflow.classification.models.enums.Effort;
import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.classification.repositories.AIClassificationRepository;
import ma.dev.workflow.classification.service.IAIClassificationService;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.issue.events.IssueClassifiedEvent;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.IssueType;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.repositories.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class AIClassificationService implements IAIClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AIClassificationService.class);

    private final AIClassificationRepository classificationRepository;
    private final IssueRepository issueRepository;
    private final AIClassificationMapper classificationMapper;

    /**
     * Above this, the suggestion is written onto the issue with nobody watching.
     *
     * <p>Configurable rather than a constant because it is a policy, not a fact: a team that finds
     * the model too eager lowers it without a rebuild. 0.85 is deliberately high — the cost of a
     * wrong auto-apply is someone quietly working the wrong ticket, while the cost of not applying
     * is one click.
     */
    private final float autoApplyThreshold;

    public AIClassificationService(AIClassificationRepository classificationRepository,
                                   IssueRepository issueRepository,
                                   AIClassificationMapper classificationMapper,
                                   @Value("${app.classification.auto-apply-threshold}") float autoApplyThreshold) {
        this.classificationRepository = classificationRepository;
        this.issueRepository = issueRepository;
        this.classificationMapper = classificationMapper;
        this.autoApplyThreshold = autoApplyThreshold;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent by lookup, not by luck. The row is found by issue id and updated if it exists,
     * so a redelivered message overwrites rather than colliding with the unique constraint and
     * ending up in the dead-letter queue for no reason.
     */
    @Override
    @Transactional
    public void record(IssueClassifiedEvent event) {
        Issue issue = issueRepository.findById(event.issueId())
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + event.issueId()));

        AIClassification classification = classificationRepository.findByIssueId(event.issueId())
                .orElseGet(AIClassification::new);
        classification.setIssue(issue);

        // Parsed leniently: the producer is another service, and one unrecognised enum value
        // should cost that field, not the whole classification. An unusable message would go to
        // the dead-letter queue and the issue would show nothing at all.
        classification.setSuggestedType(parse(event.suggestedType(), IssueType::valueOf, "type", event.issueId()));
        classification.setSuggestedPriority(parse(event.suggestedPriority(), Priority::valueOf, "priority", event.issueId()));
        classification.setEffortHint(parse(event.effortHint(), Effort::valueOf, "effort", event.issueId()));
        classification.setSuggestedTeam(event.suggestedTeam());
        classification.setSentimentScore(event.sentimentScore());
        classification.setMissingInfo(event.missingInfo());
        classification.setModelVersion(event.modelVersion());

        // Clamped rather than trusted. A model that returns 1.4 would otherwise auto-apply
        // everything, and a negative value would silently disable the feature.
        float confidence = event.confidence() == null ? 0f : Math.clamp(event.confidence(), 0f, 1f);
        classification.setConfidence(confidence);

        boolean confident = confidence >= autoApplyThreshold;
        boolean hasSomethingToApply = classification.getSuggestedType() != null
                || classification.getSuggestedPriority() != null;

        if (confident && hasSomethingToApply) {
            applyTo(issue, classification);
            classification.setReviewStatus(ReviewStatus.AUTO_APPLIED);
            log.info("Auto-applied classification to {} at {}% confidence",
                    issue.getIssueKey(), Math.round(confidence * 100));
        } else {
            classification.setReviewStatus(ReviewStatus.PENDING);
        }

        classificationRepository.save(classification);
    }

    @Override
    public Optional<AIClassificationDTO> findByIssueId(Long issueId) {
        requireIssue(issueId);
        return classificationRepository.findByIssueId(issueId).map(classificationMapper::fromModel);
    }

    @Override
    @Transactional
    public AIClassificationDTO accept(Long issueId) {
        AIClassification classification = requireClassification(issueId);
        requireNotReviewed(classification);

        applyTo(classification.getIssue(), classification);
        classification.setReviewStatus(ReviewStatus.CONFIRMED);
        return classificationMapper.fromModel(classificationRepository.save(classification));
    }

    @Override
    @Transactional
    public AIClassificationDTO override(Long issueId) {
        AIClassification classification = requireClassification(issueId);
        requireNotReviewed(classification);

        // Nothing is written to the issue. The whole point is that the human's values stand;
        // recording the disagreement is what M4 counts to get the agreement rate.
        classification.setReviewStatus(ReviewStatus.OVERRIDDEN);
        return classificationMapper.fromModel(classificationRepository.save(classification));
    }

    /**
     * Writes the suggestion onto the issue.
     *
     * <p>Only type and priority. Team, effort and sentiment have nowhere to go: {@code Issue} has
     * no such fields and the class diagram does not give it any. They stay as evidence for the
     * dashboard rather than being invented onto the entity.
     *
     * <p>Status is never touched either. An issue is born TO_DO and only the transition map moves
     * it — letting a model skip that would make the workflow rules negotiable.
     *
     * <p>This bumps the issue's version, which is correct: the row really did change. The board
     * refreshes when the suggestion arrives so its cached copy does not go stale.
     */
    private void applyTo(Issue issue, AIClassification classification) {
        if (classification.getSuggestedType() != null) {
            issue.setType(classification.getSuggestedType());
        }
        if (classification.getSuggestedPriority() != null) {
            issue.setPriority(classification.getSuggestedPriority());
        }
        issueRepository.saveAndFlush(issue);
    }

    /** A decision is made once. Accepting something already confirmed is a bug in the caller. */
    private void requireNotReviewed(AIClassification classification) {
        ReviewStatus status = classification.getReviewStatus();
        if (status == ReviewStatus.CONFIRMED || status == ReviewStatus.OVERRIDDEN) {
            throw new BusinessRuleException("ALREADY_REVIEWED",
                    "This suggestion was already " + status.name().toLowerCase() + ".");
        }
    }

    private AIClassification requireClassification(Long issueId) {
        requireIssue(issueId);
        return classificationRepository.findByIssueId(issueId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No classification for issue " + issueId + " yet."));
    }

    private Issue requireIssue(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
    }

    private <E extends Enum<E>> E parse(String value, Function<String, E> converter,
                                        String field, Long issueId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return converter.apply(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Classifier sent an unknown {} '{}' for issue {}. Ignoring that field.",
                    field, value, issueId);
            return null;
        }
    }

}
