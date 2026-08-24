package ma.dev.workflow.common.security;

/**
 * The claims auth-service writes into a token, named here so the two services agree on the
 * spelling. This is the entire contract between them: a signature, a subject and these two names.
 */
public final class TokenClaims {

    /**
     * The app_user id of the caller.
     *
     * <p>This is why no request has to ask auth-service who is calling. The id an issue stores as
     * its reporter is already in the token, verified by the signature.
     */
    public static final String USER_ID = "uid";

    /** The role name. Mapped to ROLE_&lt;value&gt; so hasRole() works. */
    public static final String ROLE = "role";

    /** The role a service gives itself when calling another service. No person ever has it. */
    public static final String SERVICE_ROLE = "SERVICE";

    private TokenClaims() {
    }
}
