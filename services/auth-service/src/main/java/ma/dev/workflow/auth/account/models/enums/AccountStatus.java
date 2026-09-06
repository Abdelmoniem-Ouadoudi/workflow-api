package ma.dev.workflow.auth.account.models.enums;

/**
 * Whether this account may log in, and why not when it may not.
 *
 * <p>Replaced a boolean at M5. {@code is_active} could say yes or no, but not <em>why</em> no, and
 * the two nos need different sentences on the screen: somebody who has just registered is waiting
 * for an administrator, somebody who was switched off should go and talk to one. Telling a new
 * person "this account is deactivated" reads as a punishment for signing up.
 */
public enum AccountStatus {

    /** Registered, waiting for an administrator. Cannot log in. */
    PENDING,

    /** Approved. */
    ACTIVE,

    /** Approved once, switched off since. The row stays — deletion is never an option here. */
    DISABLED
}
