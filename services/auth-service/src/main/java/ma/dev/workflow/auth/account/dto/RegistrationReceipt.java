package ma.dev.workflow.auth.account.dto;

import ma.dev.workflow.auth.account.models.enums.AccountStatus;

/**
 * What registration returns now: an acknowledgement, not a token.
 *
 * <p>Before M5 registering signed you in, on the reasoning that asking for the password twice in a
 * row would be theatre. That reasoning held only while registering was the same act as being
 * allowed in. It is not any more — the account exists and cannot be used — so a token would be a
 * key to a door that is locked, and the React app would have to work out from an empty project
 * list that something was wrong.
 *
 * <p>Carries {@code status} rather than only a message, so the client branches on a value instead
 * of matching English.
 */
public record RegistrationReceipt(String username, AccountStatus status, String message) {
}
