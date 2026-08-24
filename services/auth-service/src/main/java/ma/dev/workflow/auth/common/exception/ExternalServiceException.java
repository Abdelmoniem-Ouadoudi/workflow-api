package ma.dev.workflow.auth.common.exception;

/**
 * A call to another service failed in a way this service cannot fix: it was down, it timed out,
 * or it answered something unexpected. Mapped to 503, never to 500, because the fault is not here
 * and the caller may reasonably retry.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalServiceException(String message) {
        super(message);
    }
}
