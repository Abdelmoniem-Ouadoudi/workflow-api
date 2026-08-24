package ma.dev.workflow.auth.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The same error shape work-service returns, deliberately duplicated rather than extracted into
 * a shared module. A shared library would couple the deployments: changing this envelope would
 * force every service to be rebuilt and released together, which is the coupling the split was
 * meant to remove. Two copies of a small class is the cheaper trade at four services.
 *
 * <p>The React error handler is written against these field names once, so they must not change
 * without a frontend change.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final LocalDateTime timestamp = LocalDateTime.now();
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldError> fieldErrors;

    public ApiError(int status, String code, String message, String path, List<FieldError> fieldErrors) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public ApiError(int status, String code, String message, String path) {
        this(status, code, message, path, null);
    }

    @Getter
    public static class FieldError {

        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}
