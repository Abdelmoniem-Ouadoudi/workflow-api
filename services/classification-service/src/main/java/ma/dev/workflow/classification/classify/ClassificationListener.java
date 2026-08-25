package ma.dev.workflow.classification.classify;

import ma.dev.workflow.classification.classify.dto.IssueClassified;
import ma.dev.workflow.classification.classify.dto.IssueCreated;
import ma.dev.workflow.classification.classify.dto.Suggestion;
import ma.dev.workflow.classification.common.messaging.WorkflowMessaging;
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

    public ClassificationListener(Classifier classifier, RabbitTemplate rabbitTemplate) {
        this.classifier = classifier;
        this.rabbitTemplate = rabbitTemplate;
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
