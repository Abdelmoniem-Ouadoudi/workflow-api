package ma.dev.workflow.gateway.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The same error shape the two services behind this gateway return.
 *
 * <p>It matters most here. When work-service is down, the reply the browser gets is written by
 * the gateway, not by work-service - and the React error handler must not have to tell the
 * difference. One envelope means one code path in the frontend for every failure in the system.
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
