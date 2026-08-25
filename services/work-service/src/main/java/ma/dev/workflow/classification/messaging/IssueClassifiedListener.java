package ma.dev.workflow.classification.messaging;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.classification.service.IAIClassificationService;
import ma.dev.workflow.common.messaging.WorkflowMessaging;
import ma.dev.workflow.common.web.CorrelationIdFilter;
import ma.dev.workflow.issue.events.IssueClassifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Takes the model's answer off the queue and stores it.
 *
 * <p>The other half of what makes creating an issue instant: the POST returned long ago, and this
 * runs on a listener thread whenever the classifier gets round to answering.
 */
@Component
public class IssueClassifiedListener {

    private static final Logger log = LoggerFactory.getLogger(IssueClassifiedListener.class);

    private final IAIClassificationService classificationService;

    public IssueClassifiedListener(IAIClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @RabbitListener(queues = WorkflowMessaging.ISSUE_CLASSIFIED_QUEUE)
    public void onIssueClassified(IssueClassifiedEvent event,
                                  @Header(name = WorkflowMessaging.CORRELATION_HEADER, required = false)
                                  String correlationId) {
        // A listener thread has no request behind it, so the MDC starts empty. Putting the id back
        // is what keeps one user action greppable across the broker hop, which is the requirement
        // in docs/ARCHITECTURE-NOTES.md section 1.
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId == null ? "no-corr-id" : correlationId);

        try {
            classificationService.record(event);
            log.info("Stored classification for issue {}", event.issueId());

        } catch (EntityNotFoundException ex) {
            // The issue was deleted between being classified and the answer arriving. Retrying
            // cannot bring it back, so the message is rejected outright rather than bouncing three
            // times first. Not an error: someone deleted a ticket, which they are allowed to do.
            log.info("Discarding a classification for issue {}: the issue no longer exists.",
                    event.issueId());
            throw new AmqpRejectAndDontRequeueException(ex);

        } finally {
            // The thread goes back to the listener pool. Without this the next message inherits
            // this one's id, which is worse than having none.
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
