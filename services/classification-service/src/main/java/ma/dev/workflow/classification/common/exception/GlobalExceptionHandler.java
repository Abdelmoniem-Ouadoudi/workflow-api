package ma.dev.workflow.classification.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * The same envelope the other three services return, so the React error handler has one shape to
 * read no matter which service refused.
 *
 * <p>Short, because this service barely serves HTTP: two admin endpoints and one search. Most of
 * what can go wrong here happens on a listener thread, where there is no response to write and the
 * dead-letter queue is the error handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * A query parameter broke its constraint — usually a search of two characters.
     *
     * <p>400 rather than an empty result. "Your search was too short" and "nothing matched" are
     * different answers, and the panel renders them differently: one is a prompt to keep typing.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        lastNodeOf(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();

        return ResponseEntity.badRequest().body(new ApiError(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Parameter '" + ex.getParameterName() + "' is required.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint for this URL.", request);
    }

    /** Anything unforeseen. Logs the stack trace, never returns it. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error. Please try again.", request);
    }

    /** "findSimilar.text" reads as "text" to whoever filled in the form. */
    private String lastNodeOf(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), code, message, request.getRequestURI()));
    }
}
