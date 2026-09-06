package ma.dev.workflow.common.exception;

/**
 * The caller is authenticated but not allowed to do this.
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 *
 * <p>Kept separate from {@link BusinessRuleException} because the two answer different questions.
 * 422 means "the rule says no" and retrying with different data could work. 403 means "you are the
 * wrong person" and no change to the body will help. The React app treats them differently: a 422
 * lands on the form, a 403 sends you back to the project list.
 *
 * <p>Not Spring Security's {@code AccessDeniedException}: that one is thrown by the filter chain
 * before a controller runs, when a path rule fails. This one is thrown from a service, after a row
 * has been loaded, because membership of a project cannot be decided from the URL alone.
 */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
