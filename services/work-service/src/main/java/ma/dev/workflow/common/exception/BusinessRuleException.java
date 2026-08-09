package ma.dev.workflow.common.exception;

/**
 * The request was well formed but breaks a business rule.
 * Mapped to HTTP 422 by {@link GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
