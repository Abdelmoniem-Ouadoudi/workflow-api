package ma.dev.workflow.auth.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts the request's correlation id into the logging context so every line of this service's log
 * carries it. The gateway sets the header; this service only reads it. A request that arrives
 * without one still gets an id, so a direct call is never untraceable.
 *
 * <p>Why it exists: one user action crosses gateway, auth-service and work-service, and at M3 it
 * will also cross RabbitMQ. Without a single id running through all of them, "why was this slow"
 * is a question the logs cannot answer.
 *
 * <p>The MDC is thread-local and the thread goes back to Tomcat's pool, so the finally block is
 * not optional: skipping it leaks the previous request's id onto the next one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "corrId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        boolean generatedHere = incoming == null || incoming.isBlank();
        String correlationId = generatedHere ? UUID.randomUUID().toString() : incoming;

        MDC.put(MDC_KEY, correlationId);
        // Only stamp the response when the id was invented here, which means the call came
        // straight to this port rather than through the gateway. When the gateway supplied it,
        // the gateway is already putting it on its own response, and setting it again would send
        // the header twice - the same duplicated-header mistake the CORS filter used to make.
        if (generatedHere) {
            response.setHeader(HEADER, correlationId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
