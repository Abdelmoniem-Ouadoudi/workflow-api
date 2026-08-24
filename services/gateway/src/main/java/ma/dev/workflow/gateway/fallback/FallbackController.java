package ma.dev.workflow.gateway.fallback;

import jakarta.servlet.http.HttpServletRequest;
import ma.dev.workflow.gateway.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a caller gets when a circuit breaker is open.
 *
 * <p>Without it, a request to a service that is down waits for a socket timeout and holds a
 * gateway thread the whole time. Enough of those and the gateway stops answering for the services
 * that are still healthy - one failure becomes every failure. The breaker opens after a run of
 * failures and every later call returns here immediately, which both protects the gateway and
 * gives the failing service room to recover instead of being hammered while it restarts.
 *
 * <p>503 and not 500: the request was fine, the destination was not, and retrying later is a
 * reasonable thing for the caller to do.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * No {@code method} is listed, so this answers every verb. The breaker forwards the original
     * request as it was, and a POST landing on a GET-only handler would come back as a 405 that
     * explains nothing about what actually went wrong.
     */
    @RequestMapping("/{service}")
    public ResponseEntity<ApiError> unavailable(@PathVariable String service,
                                                HttpServletRequest request) {
        ApiError body = new ApiError(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                service + " is not responding. The request was not carried out.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
