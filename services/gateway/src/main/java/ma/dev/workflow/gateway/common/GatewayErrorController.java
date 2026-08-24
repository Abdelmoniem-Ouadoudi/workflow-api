package ma.dev.workflow.gateway.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
// Boot 4 moved this out of org.springframework.boot.web.servlet.error, where every Boot 3
// tutorial still puts it. Implementing it is what switches off Boot's own BasicErrorController,
// which is @ConditionalOnMissingBean on this exact interface - without it both would map /error
// and the context would fail to start on an ambiguous mapping.
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default error body with the envelope the rest of the system uses.
 *
 * <p>A URL no route matches is the common case: the gateway's route list is an allow-list, so
 * anything not on it stops here. Without this the caller would get
 * {@code {"timestamp","status","error","path"}} — close enough to look right, different enough
 * that the React error handler reads {@code code} as undefined and shows nothing.
 *
 * <p>The status is taken from the original request rather than assumed: this handles a 404 for an
 * unrouted path and a 500 from a filter with the same code.
 */
@RestController
public class GatewayErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiError> handle(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        String message = status == HttpStatus.NOT_FOUND
                ? "No route for this URL."
                : "The gateway could not complete the request.";

        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "GATEWAY_ERROR",
                message,
                originalPath == null ? request.getRequestURI() : originalPath.toString()));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(code.toString()));
        return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
    }
}
