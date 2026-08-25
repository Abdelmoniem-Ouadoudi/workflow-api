package ma.dev.workflow.classification.dto;

import ma.dev.workflow.classification.models.enums.Effort;
import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.issue.models.enums.IssueType;
import ma.dev.workflow.issue.models.enums.Priority;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The suggestion, as the React chip reads it.
 *
 * <p>A record with no validation annotations because nothing is ever posted to this shape: a
 * classification is written by the broker listener and only read over HTTP. The two ways a person
 * acts on it, accept and override, carry no body at all — the decision is the URL.
 *
 * <p>{@code issueVersion} is not on the entity. It is the issue's current optimistic-locking
 * version, put here so the board can refresh its cached copy when a suggestion is auto-applied.
 * Without it the next drag on that card would send a stale version and get a false 409 — the same
 * bug this project has already hit twice.
 */
public record AIClassificationDTO(
        Long id,
        Long issueId,
        IssueType suggestedType,
        Priority suggestedPriority,
        String suggestedTeam,
        Effort effortHint,
        Float sentimentScore,
        Float confidence,
        List<String> missingInfo,
        ReviewStatus reviewStatus,
        String modelVersion,
        Long issueVersion,
        LocalDateTime createdAt
) {
}
