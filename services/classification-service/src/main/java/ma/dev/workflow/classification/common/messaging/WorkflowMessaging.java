package ma.dev.workflow.classification.common.messaging;

/**
 * The names of the exchange, the queues and the routing keys.
 *
 * <p>A deliberate copy of the same class in work-service. Sharing it through a library would mean
 * neither service could be deployed without the other, which is the coupling the split exists to
 * remove. What the two share is the contract - these strings and the JSON shape - not a jar.
 *
 * <p>The declarations below must agree with work-service's, or RabbitMQ refuses to redeclare a
 * queue with different arguments and the service fails to start. That is the right failure: loud,
 * immediate, and pointing at the exact disagreement.
 */
public final class WorkflowMessaging {

    public static final String EXCHANGE = "workflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "workflow.dlx";

    public static final String ISSUE_CREATED_KEY = "issue.created";
    public static final String ISSUE_CLASSIFIED_KEY = "issue.classified";

    public static final String ISSUE_CREATED_QUEUE = "issue.created.q";
    public static final String ISSUE_CREATED_DLQ = "issue.created.dlq";

    /** Carried as a message header so one user action stays greppable across the broker. */
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private WorkflowMessaging() {
    }
}
