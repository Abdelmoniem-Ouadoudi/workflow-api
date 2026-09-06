package ma.dev.workflow.common.security;

import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.user.models.enums.Role;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Answers "who is making this request" from the verified token, never from the request body.
 *
 * <p>Before M2 the caller sent {@code reporterId} on an issue and {@code authorId} on a comment,
 * which meant anyone could file an issue in someone else's name. Those fields are now ignored:
 * the id comes from a claim the caller cannot alter without breaking the signature.
 */
@Component
public class CurrentUser {

    /**
     * The app_user id of the caller.
     *
     * @throws BusinessRuleException when the caller is a service rather than a person. A service
     *                               token has no {@code uid}, and silently writing null into
     *                               {@code reporter_id} would hit a not-null constraint with an
     *                               error nobody could read.
     */
    public Long requireId() {
        Jwt jwt = jwt();
        // JSON has one number type, so a claim can arrive as an Integer or a Long depending on
        // its size. Reading through Number works for both; casting to Long would fail on a big id.
        Number userId = jwt.getClaim(TokenClaims.USER_ID);
        if (userId == null) {
            throw new BusinessRuleException("NOT_A_USER",
                    "This action must be performed by a signed-in user, not by a service.");
        }
        return userId.longValue();
    }

    /**
     * The caller's global role, from the {@code role} claim.
     *
     * <p>Returns empty for a service token. {@code SERVICE} is a real value of that claim but not
     * a value of {@link Role}, so parsing it would throw; a caller asking "is this person an
     * admin" wants "no", not an exception.
     */
    public Optional<Role> role() {
        String role = jwt().getClaimAsString(TokenClaims.ROLE);
        if (role == null || TokenClaims.SERVICE_ROLE.equals(role)) {
            return Optional.empty();
        }
        return Arrays.stream(Role.values()).filter(value -> value.name().equals(role)).findFirst();
    }

    /** True only for a signed-in person whose role is ADMIN. Never true for a service token. */
    public boolean isAdmin() {
        return role().filter(Role.ADMIN::equals).isPresent();
    }

    private Jwt jwt() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        // Unreachable through the filter chain: every route here is authenticated with a JWT.
        // Kept as a loud failure rather than a null, so a future misconfiguration cannot pass
        // silently as an anonymous write.
        throw new IllegalStateException("No JWT on the security context: " + principal);
    }
}
