package ma.dev.workflow.classification.classify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An issue was deleted. Only the id matters: this service is being told to forget, not to read.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueDeleted(
        Long issueId,
        String issueKey
) {
}
