package ma.dev.workflow.issue_comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IssueCommentDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Content is required")
    private String content;

    /** Taken from the URL, not the body. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long issueId;

    /**
     * Taken from the token, never from the request. Before M2 this was writable, which meant any
     * caller could post a comment under someone else's name.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long authorId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;
}
