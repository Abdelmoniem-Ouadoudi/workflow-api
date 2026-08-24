package ma.dev.workflow.auth.common.config;

import ma.dev.workflow.auth.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Two HTTP client builders, and the difference between them is the whole point.
 *
 * <p>The first version of this class declared only the load-balanced one. That broke Eureka:
 * Spring Cloud builds Eureka's own HTTP client from whatever {@code RestClient.Builder} bean is in
 * the context, so the registry client became load-balanced too and tried to resolve the literal
 * host {@code localhost} as a service name - through the registry it had not managed to reach yet.
 * The log said {@code No instances available for localhost}, which is the symptom of a client
 * asking a directory where the directory is.
 *
 * <p>The rule that falls out of it: infrastructure calls go to an address, service calls go to a
 * name. Only the second kind wants a load balancer.
 */
@Configuration
public class RestClientConfig {

    /**
     * Plain, and primary. Eureka picks this one up to talk to the registry, which it reaches by
     * the address in {@code eureka.client.service-url}. There is nothing to balance: the registry
     * is where you go to find out where things are.
     *
     * <p>Prototype-scoped to match Spring Boot's own builder bean, so two callers cannot mutate
     * each other's configuration.
     */
    @Bean
    @Primary
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * The one used to call work-service. {@code @LoadBalanced} is what makes
     * {@code http://work-service/users} resolve: Spring Cloud LoadBalancer intercepts the call,
     * asks Eureka which instances of that name are up, and picks one. No host and no port appears
     * anywhere in this service's code or configuration, which is the reason the registry exists.
     *
     * <p>No OpenFeign. {@code RestClient} ships with Spring Framework, does the same job for one
     * call, and is one fewer dependency to justify.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder()
                // The correlation id has to survive the hop, or the two services' logs cannot be
                // stitched back together. Set on the way out, read by the filter on the way in.
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                });
    }
}
