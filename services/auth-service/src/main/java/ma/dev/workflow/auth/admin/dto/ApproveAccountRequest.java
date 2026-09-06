package ma.dev.workflow.auth.admin.dto;

import jakarta.validation.constraints.NotNull;
import ma.dev.workflow.auth.account.models.enums.Role;

/**
 * Approving somebody and saying what they are, in one act.
 *
 * <p>The role is required rather than defaulted, because approving is the moment the decision is
 * actually made. A default would let an administrator click through the queue without ever choosing
 * — which is the habit the whole approval step exists to prevent.
 */
public record ApproveAccountRequest(

        @NotNull(message = "Role is required")
        Role role) {
}
