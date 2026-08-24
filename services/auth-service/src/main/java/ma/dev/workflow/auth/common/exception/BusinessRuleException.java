package ma.dev.workflow.auth.common.exception;

import lombok.Getter;

/**
 * The request was well formed but a rule says no. Carries a stable code the frontend can switch
 * on, because a message is for a human and a code is for a program.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
