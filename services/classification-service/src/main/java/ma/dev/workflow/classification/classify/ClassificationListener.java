package ma.dev.workflow.classification.classify;

import ma.dev.workflow.classification.classify.dto.IssueClassified;
import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.IssueDeleted;
import ma.dev.workflow.classification.classify.dto.ProjectDeleted;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import ma.dev.workflow.classification.common.messaging.WorkflowMessaging;
import ma.dev.workflow.classification.similarity.IssueVectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The service's whole job: take a ticket off the queue, classify it, put the answer back.
 *
 * <p>Nothing is stored. The answer goes to work-service, which owns the row.
 */
@Component
public class ClassificationListener {

    private static final Logger log = LoggerFactory.getLogger(ClassificationListener.class);

    /** Matches the MDC key the correlation-id filter uses, so both sources agree. */
    private static final String MDC_KEY = "corrId";

    private final Classifier classifier;
    private final RabbitTemplate rabbitTemplate;
    private final IssueVectorIndex index;

    public ClassificationListener(Classifier classifier, RabbitTemplate rabbitTemplate,
                                  IssueVectorIndex index) {
        this.classifier = classifier;
        this.rabbitTemplate = rabbitTemplate;
        this.index = index;
    }

    /**
     * An issue was deleted, so forget it.
     *
     * <p>Its classification went with it through {@code ON DELETE CASCADE}, but a vector in another
     * database has no foreign key to cascade along. Left alone, the duplicate panel would keep
     * offering tickets that no longer exist — the failure would be silent and would get worse.
     */
    @RabbitListener(queues = WorkflowMessaging.ISSUE_DELETED_QUEUE)
    public void onIssueDeleted(IssueDeleted event,
                               @Header(name = WorkflowMessaging.CORRELATION_HEADER, required = false)
                               String correlationId) {
        MDC.put(MDC_KEY, correlationId == null ? "no-corr-id" : correlationId);
        try {
            index.remove(event.issueId());
            log.info("Forgot issue {}", event.issueId());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * A whole project went, and its issues with it.
     *
     * <p>The database cascaded them, which is invisible from here: no {@code issue.deleted} is
     * published for any of them, because {@code IssueService.deleteById} never ran. One message
     * for the whole cascade, matching how the deletion actually happened.
     */
    @RabbitListener(queues = WorkflowMessaging.PROJECT_DELETED_QUEUE)
    public void onProjectDeleted(ProjectDeleted event,
                                 @Header(name = WorkflowMessaging.CORRELATION_HEADER, required = false)
                                 String correlationId) {
        MDC.put(MDC_KEY, correlationId == null ? "no-corr-id" : correlationId);
        try {
            index.removeProject(event.projectKey());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @RabbitListener(queues = WorkflowMessaging.ISSUE_CREATED_QUEUE)
    public void onIssueCreated(IssueCreated issue,
                               @Header(name = WorkflowMessaging.CORRELATION_HEADER, required = false)
                               String correlationId) {
        // A listener thread has no request behind it, so the MDC starts empty. Restoring the id
        // from the message header is what lets one grep still cover the gateway, work-service and
        // this service after the hop through the broker.
        MDC.put(MDC_KEY, correlationId == null ? "no-corr-id" : correlationId);

        try {
            if (issue.issueId() == null) {
                // Nowhere to send an answer. Retrying cannot add an id that was never there.
                throw new AmqpRejectAndDontRequeueException(
                        "issue.created arrived with no issueId. Parking it.");
            }

            // Remember the ticket before classifying it. Deliberately first: indexing is local and
            // cannot fail for an external reason, while the classifier can. Doing it first means a
            // Groq outage costs the suggestion but not the duplicate detection - two features that
            // arrive on the same message should not share one failure.
            index.index(issue);

            Suggestion suggestion = classifier.classify(issue);
            publish(issue, suggestion, correlationId);

            log.info("Classified {} as {}/{} at {}% confidence",
                    issue.issueKey(), suggestion.type(), suggestion.priority(),
                    Math.round(orZero(suggestion.confidence()) * 100));

        } catch (ClassificationFailedException failure) {
            if (failure.isPermanent()) {
                // Straight to the dead-letter queue. Three retries would only prove a second time
                // what is already known: this will not succeed. Parking it keeps the queue moving
                // for every other ticket, which is the entire point of having a DLQ.
                log.error("Parking {}: {}", issue.issueKey(), failure.getMessage());
                throw new AmqpRejectAndDontRequeueException(failure);
            }
            // Transient. Thrown on so Spring AMQP's retry can back off and try again; after the
            // configured attempts it rejects the message and the broker routes it to the DLQ.
            log.warn("Retrying {}: {}", issue.issueKey(), failure.getMessage());
            throw failure;

        } finally {
            // The thread returns to the listener pool. Without this the next message inherits this
            // one's correlation id, which is worse than having none at all.
            MDC.remove(MDC_KEY);
        }
    }

    private void publish(IssueCreated issue, Suggestion suggestion, String correlationId) {
        IssueClassified answer = new IssueClassified(
                issue.issueId(),
                suggestion.type(),
                suggestion.priority(),
                suggestion.team(),
                suggestion.effort(),
                suggestion.sentiment(),
                orZero(suggestion.confidence()),
                suggestion.missingInfo() == null ? List.of() : suggestion.missingInfo(),
                classifier.modelVersion());

        rabbitTemplate.convertAndSend(
                WorkflowMessaging.EXCHANGE,
                WorkflowMessaging.ISSUE_CLASSIFIED_KEY,
                answer,
                message -> {
                    if (correlationId != null) {
                        message.getMessageProperties()
                                .setHeader(WorkflowMessaging.CORRELATION_HEADER, correlationId);
                    }
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });
    }

    /**
     * A missing confidence is treated as zero, not as certainty.
     *
     * <p>Null would otherwise reach work-service and be clamped there anyway, but the safe reading
     * belongs at the source: a model that forgot to say how sure it was has not earned an
     * auto-apply.
     */
    private float orZero(Float value) {
        return value == null ? 0f : value;
    }
}
