package ma.dev.workflow.auth.admin.dto;

import jakarta.validation.constraints.NotNull;
import ma.dev.workflow.auth.account.models.enums.Role;

/** Changing somebody's global role after the fact, without deleting and re-creating them. */
public record ChangeRoleRequest(

        @NotNull(message = "Role is required")
        Role role) {
}
