package ma.dev.workflow.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The broker topology, declared as beans.
 *
 * <p>In code rather than clicked into the management UI, because a queue created by hand exists
 * only on the machine where someone clicked it. These declarations are idempotent: whichever
 * service starts first creates them and the others agree, which is why classification-service
 * declares the same shapes without the two fighting.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange workflowExchange() {
        return new TopicExchange(WorkflowMessaging.EXCHANGE, true, false);
    }

    /** Failures are routed here. Separate from the main exchange so the two cannot be confused. */
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * What work-service consumes: the model's answer coming back.
     *
     * <p>The dead-letter arguments are the whole point. Without them a message this service cannot
     * store is either dropped or retried forever, and both are invisible.
     */
    @Bean
    public Queue issueClassifiedQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_CLASSIFIED_QUEUE)
                .deadLetterExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WorkflowMessaging.ISSUE_CLASSIFIED_DLQ)
                .build();
    }

    @Bean
    public Queue issueClassifiedDeadLetterQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_CLASSIFIED_DLQ).build();
    }

    @Bean
    public Binding issueClassifiedBinding(Queue issueClassifiedQueue, TopicExchange workflowExchange) {
        return BindingBuilder.bind(issueClassifiedQueue)
                .to(workflowExchange)
                .with(WorkflowMessaging.ISSUE_CLASSIFIED_KEY);
    }

    @Bean
    public Binding issueClassifiedDeadLetterBinding(Queue issueClassifiedDeadLetterQueue,
                                                    TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(issueClassifiedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(WorkflowMessaging.ISSUE_CLASSIFIED_DLQ);
    }

    /**
     * JSON on the wire, not Java serialization.
     *
     * <p>Java serialization would tie both services to the same class in the same package, which
     * is exactly the coupling two services must not have. JSON means the consumer can read the
     * fields it cares about and ignore a field the producer added.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setExchange(WorkflowMessaging.EXCHANGE);
        return template;
    }

    /**
     * Declared by hand, and it must be.
     *
     * <p>Spring Boot normally provides this, but its RabbitAdmin sits in the same auto-configuration
     * class as its RabbitTemplate — so declaring a template above makes the whole block back off and
     * takes the admin with it. That failure is quiet in the worst way: it is the RabbitAdmin that
     * walks the Queue, Exchange and Binding beans above and actually creates them on the broker.
     * Without it they are objects in a context that never reach RabbitMQ, and the first symptom is
     * a listener waiting on a queue that does not exist.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
