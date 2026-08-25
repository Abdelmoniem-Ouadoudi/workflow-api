package ma.dev.workflow.classification.classify;

/**
 * A ticket could not be classified.
 *
 * <p>{@code permanent} is the field that decides where the message goes, and it is the whole
 * design of the dead-letter queue in one boolean:
 *
 * <ul>
 *   <li>{@code false} — the model was rate-limited, timed out, or the service was briefly down.
 *       Trying again in a second is likely to work, so the message is retried.</li>
 *   <li>{@code true} — the key is wrong, or the model answered prose where JSON was required.
 *       No number of retries changes that, so the message is parked in the dead-letter queue
 *       immediately rather than being tried three times to prove a point.</li>
 * </ul>
 *
 * <p>Getting this distinction wrong is how a dead-letter queue becomes useless: treat everything
 * as permanent and a rate limit loses the ticket; treat everything as transient and one malformed
 * ticket blocks the queue forever.
 */
public class ClassificationFailedException extends RuntimeException {

    private final boolean permanent;

    private ClassificationFailedException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** Worth trying again: a 429, a 503, a timeout. */
    public static ClassificationFailedException transientFailure(String message, Throwable cause) {
        return new ClassificationFailedException(message, cause, false);
    }

    /** Never going to work: a bad key, or a reply that is not the JSON that was asked for. */
    public static ClassificationFailedException permanentFailure(String message, Throwable cause) {
        return new ClassificationFailedException(message, cause, true);
    }

    public boolean isPermanent() {
        return permanent;
    }
}
