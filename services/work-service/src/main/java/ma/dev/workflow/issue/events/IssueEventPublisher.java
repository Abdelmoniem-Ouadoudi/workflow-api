package ma.dev.workflow.issue.events;

import ma.dev.workflow.common.messaging.WorkflowMessaging;
import ma.dev.workflow.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
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
        // Read before the send: the listener runs on the request thread, so the MDC still holds
        // the id the gateway set. Reading it here rather than inside the post-processor keeps the
        // failure path below able to log it too.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        try {
            rabbitTemplate.convertAndSend(
                    WorkflowMessaging.EXCHANGE,
                    WorkflowMessaging.ISSUE_CREATED_KEY,
                    event,
                    message -> {
                        if (correlationId != null) {
                            // The HTTP header stops at the queue. Carrying it in the message is
                            // what lets one grep still find both services after the hop.
                            message.getMessageProperties()
                                    .setHeader(WorkflowMessaging.CORRELATION_HEADER, correlationId);
                        }
                        // Survives a broker restart. A classification request is cheap to redo but
                        // impossible to recover once the queue has forgotten it.
                        message.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return message;
                    });

            log.debug("Published {} for issue {}", WorkflowMessaging.ISSUE_CREATED_KEY, event.issueKey());

        } catch (AmqpException ex) {
            // Swallowed on purpose. The transaction has already committed, so throwing would not
            // undo the issue - it would only turn a successful creation into a 500 for the user.
            // The ticket is real and usable; only its suggestion is missing. Logged at error with
            // the key, so the ones that need replaying can be found.
            log.error("Issue {} was created but could not be announced to the broker. "
                    + "It will not be classified until this message is replayed.", event.issueKey(), ex);
        }
    }
}
