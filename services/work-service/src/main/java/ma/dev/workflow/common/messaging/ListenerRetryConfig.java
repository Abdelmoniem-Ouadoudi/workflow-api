package ma.dev.workflow.common.messaging;

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
 * <p>Spring Boot's {@code spring.rabbitmq.listener.simple.retry.*} properties apply one policy to
 * every exception, and the distinction that matters here cannot be expressed with them. A
 * classification whose issue has since been deleted is never going to succeed; retrying it three
 * times over seven seconds proves that twice more for nothing.
 *
 * <p>{@code excludes(AmqpRejectAndDontRequeueException.class)} is the fix: that is what the
 * listener throws once it has decided a failure is permanent, so it now skips retrying and is
 * dead-lettered at once. A database blip or a restart still gets its attempts with backoff.
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
                        .excludes(AmqpRejectAndDontRequeueException.class))
                // Reject without requeueing once the attempts are gone. That, plus
                // x-dead-letter-exchange on the queue, is what actually moves a message to the
                // dead-letter queue instead of back onto the work queue.
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());

        return factory;
    }
}
