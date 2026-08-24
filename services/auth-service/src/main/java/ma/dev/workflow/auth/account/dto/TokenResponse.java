package ma.dev.workflow.auth.account.dto;

import ma.dev.workflow.auth.account.models.enums.Role;

import java.time.Instant;

/**
 * Returned by both register and login, so registering signs you in with no second round trip.
 *
 * <p>{@code userId} is the work-service id, not the account id. It is the value the React app
 * puts on an issue as reporter and on a comment as author, and the only id the rest of the
 * system knows about.
 */
public record TokenResponse(
        String token,
        Instant expiresAt,
        Long userId,
        String username,
        Role role
) {
}
