package ma.dev.workflow.issue.events;

import ma.dev.workflow.common.messaging.WorkflowMessaging;
import ma.dev.workflow.common.web.CorrelationIdFilter;
import ma.dev.workflow.project.events.ProjectDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puts {@link IssueCreatedEvent} on the broker, once the issue really exists.
 *
 * <p>{@code AFTER_COMMIT} is the entire reason this class is separate from the service. Publishing
 * inside {@code IssueService.create} would send the message while the transaction is still open,
 * so classification-service could ask work-service for an issue id that is not visible yet — or
 * worse, one belonging to a transaction that then rolled back, leaving a classification for a
 * ticket that never existed.
 *
 * <p>{@code IssueService} publishes a plain Spring event and stays unaware that a broker exists.
 * The AMQP knowledge lives here, in one class, which is also what makes the service testable
 * without a running RabbitMQ.
 *
 * <p><b>The honest gap:</b> commit and publish are still two steps. A crash in the gap between them
 * loses the message, and the issue is then never classified. That is the same dual-write problem as
 * registration at M2 and it has the same real answer, a transactional outbox. It is survivable here
 * for a reason worth saying out loud: an unclassified issue is degraded, not corrupt. The ticket
 * exists, the board shows it, and the suggestion is the only thing missing. Recorded in
 * docs/BACKLOG.md.
 */
@Component
public class IssueEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IssueEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public IssueEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(IssueCreatedEvent event) {
        send(WorkflowMessaging.ISSUE_CREATED_KEY, event, event.issueKey(),
                "It will not be classified until this message is replayed.");
    }

    /**
     * M4. Tells the classifier to forget a deleted issue.
     *
     * <p>Also after commit, and for a sharper reason than creation: publishing before the delete
     * commits would drop the vector for an issue that is still there if the transaction rolls back,
     * and it would stop being suggested while still existing.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(IssueDeletedEvent event) {
        send(WorkflowMessaging.ISSUE_DELETED_KEY, event, event.issueKey(),
                "Its vector will keep being offered as a possible duplicate until it is reindexed.");
    }

    /**
     * M4. A whole project went, and its issues with it.
     *
     * <p>Lives here rather than in a project-specific publisher because what it announces is the
     * disappearance of issues — the classifier reacts to it exactly as it reacts to one deletion,
     * only wider.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ProjectDeletedEvent event) {
        send(WorkflowMessaging.PROJECT_DELETED_KEY, event, event.projectKey(),
                "The vectors for its issues will keep being offered until they are reindexed.");
    }

    /**
     * One send, so every event carries the correlation id and survives a broker restart the same
     * way. The difference between them is a routing key and what it costs when it fails.
     */
    private void send(String routingKey, Object payload, String issueKey, String consequence) {
        // Read before the send: the listener runs on the request thread, so the MDC still holds
        // the id the gateway set. Reading it here rather than inside the post-processor keeps the
        // failure path below able to log it too.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        try {
            rabbitTemplate.convertAndSend(
                    WorkflowMessaging.EXCHANGE,
                    routingKey,
                    payload,
                    message -> {
                        if (correlationId != null) {
                            // The HTTP header stops at the queue. Carrying it in the message is
                            // what lets one grep still find both services after the hop.
                            message.getMessageProperties()
                                    .setHeader(WorkflowMessaging.CORRELATION_HEADER, correlationId);
                        }
                        // Survives a broker restart. These messages are cheap to redo but
                        // impossible to recover once the queue has forgotten them.
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });

            log.debug("Published {} for issue {}", routingKey, issueKey);

        } catch (AmqpException ex) {
            // Swallowed on purpose. The transaction has already committed, so throwing would not
            // undo the write - it would only turn a successful request into a 500 for the user.
            // Logged at error with the key, so the ones that need replaying can be found.
            log.error("Issue {} was changed but {} could not be announced to the broker. {}",
                    issueKey, routingKey, consequence, ex);
        }
    }
}
