package ma.dev.workflow.classification.classify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The ticket, as it arrives off the queue.
 *
 * <p>{@code ignoreUnknown} is the versioning strategy. work-service can add a field to the event
 * without this service being redeployed first; without it, one new field on the producer would
 * send every message straight to the dead-letter queue. Tolerant readers are what let two services
 * ship on different days.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueCreated(
        Long issueId,
        String issueKey,
        String title,
        String description,
        String projectKey
) {
}
