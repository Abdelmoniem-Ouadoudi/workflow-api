package ma.dev.workflow.classification.classify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A project was deleted, and its issues went with it.
 *
 * <p>The key rather than the id is what matters here: it is the metadata every vector in that
 * project is tagged with, so it is what they are deleted by.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDeleted(
        Long projectId,
        String projectKey
) {
}
