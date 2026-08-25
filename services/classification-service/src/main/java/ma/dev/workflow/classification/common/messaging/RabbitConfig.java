package ma.dev.workflow.classification.common.messaging;

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
 * The half of the topology this service owns: the queue it reads from, and where its failures go.
 *
 * <p>Declaring queues is idempotent, so it does not matter which service starts first. It does
 * matter that both describe them identically - RabbitMQ refuses to redeclare a queue with
 * different arguments, and the service that disagrees will not start.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange workflowExchange() {
        return new TopicExchange(WorkflowMessaging.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * The work queue.
     *
     * <p>The two dead-letter arguments are what docs/ARCHITECTURE-NOTES.md section 2 asks for.
     * Without them a ticket the model can never classify is retried until the end of time, holding
     * up every ticket behind it - and nothing anywhere says so.
     */
    @Bean
    public Queue issueCreatedQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_CREATED_QUEUE)
                .deadLetterExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WorkflowMessaging.ISSUE_CREATED_DLQ)
                .build();
    }

    /**
     * Where a message goes when it cannot be classified.
     *
     * <p>A plain durable queue with no consumer, on purpose. Nothing drains it automatically:
     * messages sit here until somebody looks, and the replay endpoint puts them back. A queue that
     * empties itself while nobody is watching is a queue that loses things quietly.
     */
    @Bean
    public Queue issueCreatedDeadLetterQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_CREATED_DLQ).build();
    }

    @Bean
    public Binding issueCreatedBinding(Queue issueCreatedQueue, TopicExchange workflowExchange) {
        return BindingBuilder.bind(issueCreatedQueue)
                .to(workflowExchange)
                .with(WorkflowMessaging.ISSUE_CREATED_KEY);
    }

    @Bean
    public Binding issueCreatedDeadLetterBinding(Queue issueCreatedDeadLetterQueue,
                                                 TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(issueCreatedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(WorkflowMessaging.ISSUE_CREATED_DLQ);
    }

    /**
     * M4. Deletions, so a vector does not outlive its issue.
     *
     * <p>Its own queue rather than sharing the created one. They carry different payloads and fail
     * for different reasons: a deletion that cannot be processed should not sit behind a backlog
     * of classifications waiting on an external model.
     */
    @Bean
    public Queue issueDeletedQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_DELETED_QUEUE)
                .deadLetterExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WorkflowMessaging.ISSUE_DELETED_DLQ)
                .build();
    }

    @Bean
    public Queue issueDeletedDeadLetterQueue() {
        return QueueBuilder.durable(WorkflowMessaging.ISSUE_DELETED_DLQ).build();
    }

    @Bean
    public Binding issueDeletedBinding(Queue issueDeletedQueue, TopicExchange workflowExchange) {
        return BindingBuilder.bind(issueDeletedQueue)
                .to(workflowExchange)
                .with(WorkflowMessaging.ISSUE_DELETED_KEY);
    }

    @Bean
    public Binding issueDeletedDeadLetterBinding(Queue issueDeletedDeadLetterQueue,
                                                 TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(issueDeletedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(WorkflowMessaging.ISSUE_DELETED_DLQ);
    }

    /**
     * M4. Project deletions, which take a whole project's issues with them.
     *
     * <p>Its own queue for the same reason issue.deleted has one: different payload, different
     * failure, and a forget-everything message should not queue behind classifications waiting on
     * an external model.
     */
    @Bean
    public Queue projectDeletedQueue() {
        return QueueBuilder.durable(WorkflowMessaging.PROJECT_DELETED_QUEUE)
                .deadLetterExchange(WorkflowMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WorkflowMessaging.PROJECT_DELETED_DLQ)
                .build();
    }

    @Bean
    public Queue projectDeletedDeadLetterQueue() {
        return QueueBuilder.durable(WorkflowMessaging.PROJECT_DELETED_DLQ).build();
    }

    @Bean
    public Binding projectDeletedBinding(Queue projectDeletedQueue, TopicExchange workflowExchange) {
        return BindingBuilder.bind(projectDeletedQueue)
                .to(workflowExchange)
                .with(WorkflowMessaging.PROJECT_DELETED_KEY);
    }

    @Bean
    public Binding projectDeletedDeadLetterBinding(Queue projectDeletedDeadLetterQueue,
                                                   TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(projectDeletedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(WorkflowMessaging.PROJECT_DELETED_DLQ);
    }

    /** JSON, so neither service needs the other's classes on its classpath. */
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
     *
     * <p>It is also what {@code DeadLetterService} uses to count what is parked.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
