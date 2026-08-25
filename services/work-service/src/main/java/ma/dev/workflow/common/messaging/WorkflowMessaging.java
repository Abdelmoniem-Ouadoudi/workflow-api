package ma.dev.workflow.common.messaging;

/**
 * The names of the exchange, the queues and the routing keys.
 *
 * <p>These strings are the contract between work-service and classification-service. They are
 * duplicated in the other service rather than shared through a library, for the same reason the
 * error envelope is: a shared module would mean the two services can never be deployed
 * independently, which is the coupling the split was meant to remove.
 *
 * <p>Naming, so a queue's job is readable from its name alone: {@code <event>.q} is consumed by
 * somebody, {@code <event>.dlq} is where its failures are parked.
 */
public final class WorkflowMessaging {

    /**
     * A topic exchange, not a direct one. Direct routes by an exact key and would need a new
     * binding for every event; topic routes by pattern, so a future consumer of
     * {@code issue.*} attaches without anything here changing.
     */
    public static final String EXCHANGE = "workflow.events";

    /** Where failed messages are routed. Its own exchange, so a DLQ binding cannot collide. */
    public static final String DEAD_LETTER_EXCHANGE = "workflow.dlx";

    /** work-service publishes this after the issue row is committed. */
    public static final String ISSUE_CREATED_KEY = "issue.created";

    /** classification-service publishes this once the model has answered. */
    public static final String ISSUE_CLASSIFIED_KEY = "issue.classified";

    /** Consumed by classification-service. */
    public static final String ISSUE_CREATED_QUEUE = "issue.created.q";

    /** Consumed by work-service. */
    public static final String ISSUE_CLASSIFIED_QUEUE = "issue.classified.q";

    /** Where an issue.created message lands when it cannot be classified. */
    public static final String ISSUE_CREATED_DLQ = "issue.created.dlq";

    /** Where an issue.classified message lands when work-service cannot store it. */
    public static final String ISSUE_CLASSIFIED_DLQ = "issue.classified.dlq";

    /**
     * The correlation id, carried as a message header so one user action can still be followed
     * across the broker. docs/ARCHITECTURE-NOTES.md section 1 asks for exactly this: the HTTP
     * header alone stops at the queue.
     */
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private WorkflowMessaging() {
    }
}
