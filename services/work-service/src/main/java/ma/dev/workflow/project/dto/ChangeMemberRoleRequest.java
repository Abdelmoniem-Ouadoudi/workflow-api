package ma.dev.workflow.project.dto;

import jakarta.validation.constraints.NotNull;
import ma.dev.workflow.project.models.enums.ProjectRole;

/** Promoting a member to project manager, or standing one back down. */
public record ChangeMemberRoleRequest(

        @NotNull(message = "Project role is required")
        ProjectRole role) {
}
