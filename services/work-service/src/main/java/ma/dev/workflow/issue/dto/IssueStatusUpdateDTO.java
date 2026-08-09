package ma.dev.workflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.issue.models.enums.Status;

/** Body of PATCH /issues/{id}/status — what the Kanban board sends on a drop. */
@Getter
@Setter
public class IssueStatusUpdateDTO {

    @NotNull(message = "Status is required")
    private Status status;

    /**
     * The version the client last saw. Optional, but when it is sent and no longer matches,
     * the request is rejected with 409 instead of overwriting someone else's change.
     */
    private Long version;
}
