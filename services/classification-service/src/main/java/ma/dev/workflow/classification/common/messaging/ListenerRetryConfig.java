package ma.dev.workflow.classification.common.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Retry that knows the difference between "try again" and "this will never work".
 *
 * <p>Spring Boot's {@code spring.rabbitmq.listener.simple.retry.*} properties give a blanket
 * policy: every exception is retried the same number of times. That is wrong in one direction
 * that matters. A ticket the model can never classify — a message with no issue id, a wrong API
 * key — was being retried three times over seven seconds to prove a second and third time what
 * was already known, and with a wrong key that means three failed API calls per ticket.
 *
 * <p>{@code excludes(AmqpRejectAndDontRequeueException.class)} is the fix. That exception is what
 * the listeners throw when they have decided a failure is permanent, so it now skips retrying
 * entirely and goes to the dead-letter queue at once. Everything else — a rate limit, a timeout,
 * a service that is briefly down — still gets its three attempts with backoff.
 *
 * <p>Written as an interceptor rather than properties because the properties cannot express
 * "except this one". The properties are removed so there is only one place that decides.
 */
@Configuration
public class ListenerRetryConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);

        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .configureRetryPolicy(policy -> policy
                        .maxRetries(2)  // the first attempt plus two retries
                        .delay(Duration.ofSeconds(1))
                        .multiplier(2)
                        .maxDelay(Duration.ofSeconds(10))
                        // The listener throws this once it has decided the failure is permanent.
                        // Retrying it would only delay the honest answer.
                        .excludes(AmqpRejectAndDontRequeueException.class))
                // Once the attempts are used up, reject without requeueing. That, plus
                // x-dead-letter-exchange on the queue, is what actually moves the message to the
                // dead-letter queue rather than back onto the work queue.
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());

        return factory;
    }
}
