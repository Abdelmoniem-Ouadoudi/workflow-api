package ma.dev.workflow.classification.classify.dto;

import java.util.List;

/**
 * The answer, going back over the broker.
 *
 * <p>Field-for-field what work-service's {@code IssueClassifiedEvent} expects. The two records are
 * copies in different services on purpose — this is the contract, and a shared jar would mean
 * neither could be deployed alone.
 *
 * <p>{@code modelVersion} travels with the answer rather than being assumed on the other side.
 * It is what makes a run of bad suggestions traceable to one model, and what stops the keyword
 * stub passing itself off as AI.
 */
public record IssueClassified(
        Long issueId,
        String suggestedType,
        String suggestedPriority,
        String suggestedTeam,
        String effortHint,
        Float sentimentScore,
        Float confidence,
        List<String> missingInfo,
        String modelVersion
) {
}
