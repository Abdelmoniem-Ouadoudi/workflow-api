package ma.dev.workflow.classification.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The circuit breaker around the Groq call.
 *
 * <p>The numbers are copied from the gateway's breaker on purpose. docs/ARCHITECTURE-NOTES.md
 * section 4 makes the point that protecting one remote call and not another is an inconsistency a
 * jury notices; using the same settings in both places means there is one story about resilience
 * in this system rather than two.
 *
 * <p>What it is actually for: without it, a Groq outage means every listener thread sits waiting
 * for its own timeout, and this service stops processing anything — including the messages it
 * could have parked. Failing fast is what keeps the queue moving.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> groqCircuitBreaker() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        // Small because this is a demo: five calls is enough to watch the breaker
                        // trip during a defence. Production counts in hundreds, so one bad minute
                        // does not take a healthy provider out of service.
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        // Generous, because a large model genuinely takes seconds to answer. It is
                        // still a limit: a hung connection must become a counted failure rather
                        // than holding a listener thread until the broker gives up on it.
                        .timeoutDuration(Duration.ofSeconds(30))
                        .build())
                .build(), "groq");
    }
}
