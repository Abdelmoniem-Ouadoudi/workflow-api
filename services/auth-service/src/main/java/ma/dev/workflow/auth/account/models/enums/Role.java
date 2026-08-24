package ma.dev.workflow.auth.account.models.enums;

/**
 * Deliberately the same three values as work-service's Role, because the token carries this
 * name across and work-service turns it back into an authority. The two enums are copies:
 * a shared module for three constants would couple the deployments for nothing.
 */
public enum Role {
    DEVELOPER,
    MANAGER,
    ADMIN
}
