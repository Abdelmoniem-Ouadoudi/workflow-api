package ma.dev.workflow.issue_status_change.dto;

import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.issue.models.enums.Status;

import java.time.LocalDateTime;

/**
 * One line of the history, read-only all the way through.
 *
 * <p>No {@code @JsonProperty(READ_ONLY)} markers and no validation, because nothing in this shape
 * ever arrives from a client: history is written by the move itself, and the API offers no way to
 * post, edit or delete a row.
 */
@Getter
@Setter
public class IssueStatusChangeDTO {

    private Long id;

    private Long issueId;

    private Status fromStatus;

    private Status toStatus;

    private Long changedById;

    /**
     * Carried on the row rather than looked up by the caller.
     *
     * <p>The comment list resolves its author names against the project's members, which works
     * only for people still on the project. A history row outlives that: whoever moved the card
     * may since have left, and the answer to "who moved this" must not become "user 7".
     */
    private String changedByUsername;

    private LocalDateTime changedAt;
}
