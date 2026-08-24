package ma.dev.workflow.auth.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the error envelope straight to the response.
 *
 * <p>Needed because Spring Security rejects a request inside the filter chain, before any
 * controller runs, so {@code @RestControllerAdvice} never sees it. Without this, a 401 comes back
 * with an empty body and the React handler has nothing to read. One writer keeps the shape
 * identical whether the refusal came from a filter or from a controller.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ApiError(status.value(), code, message, request.getRequestURI()));
    }
}
