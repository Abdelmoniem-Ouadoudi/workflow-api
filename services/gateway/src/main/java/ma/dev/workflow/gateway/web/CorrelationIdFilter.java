package ma.dev.workflow.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where the correlation id is born.
 *
 * <p>The browser sends none, so the gateway makes one and every service downstream inherits it.
 * One user action then produces one id across the gateway log, the auth-service log and the
 * work-service log - and at M3, across the RabbitMQ hop too. Without it, "why was this request
 * slow" is a question four separate logs cannot answer together.
 *
 * <p>Setting a response header would not be enough: the id has to travel <em>downstream</em>. The
 * request is therefore wrapped so the header appears to have been there all along, and the
 * gateway's proxy copies it out with the rest.
 *
 * <p>Runs at the highest precedence, ahead of Spring Security, so that a rejected request is
 * logged with an id too. The MDC is thread-local and the thread returns to Tomcat's pool, so the
 * finally block is not optional: without it the next request inherits this one's id.
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
        // An id supplied by the caller is kept, so a trace that starts outside this system is not
        // broken at the door.
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(new WithCorrelationId(request, correlationId), response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Makes the id look like a header the caller sent, so the proxy forwards it downstream. */
    private static final class WithCorrelationId extends HttpServletRequestWrapper {

        private final String correlationId;

        private WithCorrelationId(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(correlationId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            // A LinkedHashSet, not a plain list: when the caller already sent the header, adding it
            // again would make the name appear twice and some clients would then send it twice.
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(HEADER);
            return Collections.enumeration(names);
        }
    }
}
