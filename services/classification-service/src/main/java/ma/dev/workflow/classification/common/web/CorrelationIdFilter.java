package ma.dev.workflow.classification.common.web;

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
 * The HTTP half of correlation, for the few endpoints this service exposes.
 *
 * <p>Most of this service's work arrives over the queue, where the id comes off a message header
 * instead — see {@code ClassificationListener}. Both write to the same MDC key, so one grep covers
 * both paths.
 *
 * <p>Only stamps the response when it generated the id itself, meaning the call did not come
 * through the gateway. When the gateway supplied it, the gateway is already putting it on its own
 * response, and setting it again would send the header twice.
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
