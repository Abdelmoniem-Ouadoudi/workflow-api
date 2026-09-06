package ma.dev.workflow.project.dto;

import jakarta.validation.constraints.NotNull;
import ma.dev.workflow.project.models.enums.ProjectRole;

/**
 * A project manager putting somebody on the project directly, without waiting to be asked.
 *
 * <p>By id and not by username: the manager picks from a list the server gave them, so there is
 * nothing to spell wrong, and an unknown id is a bug rather than a typo.
 */
public record AddMemberRequest(

        @NotNull(message = "User id is required")
        Long userId,

        @NotNull(message = "Project role is required")
        ProjectRole role) {
}
