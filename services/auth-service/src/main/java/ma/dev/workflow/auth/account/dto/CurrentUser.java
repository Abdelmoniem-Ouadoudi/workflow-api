package ma.dev.workflow.auth.account.dto;

import ma.dev.workflow.auth.account.models.enums.Role;

/**
 * What {@code GET /auth/me} answers. Read straight off the verified token, not the database:
 * if the signature holds, the claims are already trustworthy and a query would add nothing.
 */
public record CurrentUser(
        Long userId,
        String username,
        Role role
) {
}
