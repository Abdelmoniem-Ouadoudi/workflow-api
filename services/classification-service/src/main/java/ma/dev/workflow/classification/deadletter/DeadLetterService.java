package ma.dev.workflow.classification.deadletter;

import ma.dev.workflow.classification.common.messaging.WorkflowMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Makes the dead-letter queue operable rather than merely observable.
 *
 * <p>A queue you can only fall into is a bin. Everything parked there failed for a reason that was
 * true at the time — a missing API key, a provider outage — and once the reason is gone the work
 * is still worth doing. Replay is what turns "the tickets created during the outage are
 * unclassified forever" into "they catch up".
 *
 * <p>This is the second half of M3's acceptance test: remove the key, watch tickets still get
 * created and their classifications park; restore the key, replay, watch the suggestions arrive.
 */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    /**
     * A ceiling on one replay call, so a queue holding thousands cannot be dumped back onto a
     * provider that has only just recovered — which would trip the circuit breaker and park them
     * all over again. Call it repeatedly until it reports zero.
     */
    private static final int MAX_PER_REPLAY = 100;

    /** Set on a replayed message, so the management UI shows which ones have been round twice. */
    private static final String REPLAYED_HEADER = "X-Replayed";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public DeadLetterService(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    /** How many messages are parked. Zero is the answer you want. */
    public int count() {
        Properties properties = rabbitAdmin.getQueueProperties(WorkflowMessaging.ISSUE_CREATED_DLQ);
        if (properties == null) {
            return 0;
        }
        Object messageCount = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return messageCount == null ? 0 : ((Number) messageCount).intValue();
    }

    /**
     * Moves parked messages back onto the work queue.
     *
     * <p>The raw {@link Message} is moved, not a deserialized-and-re-serialized copy. Two reasons,
     * and the first one was a bug before it was a principle:
     *
     * <ul>
     *   <li>{@code receiveAndConvert} has no idea what type to produce outside a listener — a
     *       {@code @RabbitListener} gets that from its method signature, a bare receive does not,
     *       so JSON came back as a {@code LinkedHashMap} and the cast blew up.</li>
     *   <li>Headers survive. The correlation id travels in one, so a replayed message stays
     *       attached to the request that originally created the issue — which is exactly when
     *       someone is trying to work out what happened.</li>
     * </ul>
     *
     * <p>Receive-then-publish, one at a time. If publishing fails, the message has already left the
     * dead-letter queue and is gone; that is why the failure is logged loudly. A stronger version
     * would move it inside a transaction, and that is recorded in docs/BACKLOG.md rather than
     * pretended away.
     */
    public int replay() {
        int moved = 0;

        while (moved < MAX_PER_REPLAY) {
            Message parked = rabbitTemplate.receive(WorkflowMessaging.ISSUE_CREATED_DLQ);
            if (parked == null) {
                break;  // the queue is empty
            }

            try {
                // Marks it as a second attempt, visible in the management UI. Worth having: a
                // message that keeps coming back is telling you its cause was never transient.
                parked.getMessageProperties().setHeader(REPLAYED_HEADER, true);
                parked.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);

                rabbitTemplate.send(
                        WorkflowMessaging.EXCHANGE,
                        WorkflowMessaging.ISSUE_CREATED_KEY,
                        parked);
                moved++;

            } catch (RuntimeException ex) {
                log.error("Failed to replay a message after taking it off the dead-letter queue. "
                        + "That message is now lost. Body was: {}",
                        new String(parked.getBody(), StandardCharsets.UTF_8), ex);
                throw ex;
            }
        }

        log.info("Replayed {} dead-lettered classification requests.", moved);
        return moved;
    }
}
