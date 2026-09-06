package ma.dev.workflow.auth.admin.dto;

import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;

import java.time.LocalDateTime;

/**
 * One person as the administrator's screen shows them: half from {@code account} here, half from
 * {@code app_user} in work-service.
 *
 * <p>{@code email} is nullable, and not out of carelessness. It comes from the other service, so it
 * is missing when work-service could not be reached, or when a registration half-failed and left an
 * account whose profile was never created. Showing the row with a blank email is more useful than
 * hiding somebody who exists — an administrator who cannot see the broken row cannot fix it.
 */
public record AdminAccountDTO(
        Long id,
        Long workUserId,
        String username,
        String email,
        Role role,
        AccountStatus status,
        LocalDateTime createdAt) {
}
