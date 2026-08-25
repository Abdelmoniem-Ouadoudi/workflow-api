package ma.dev.workflow.classification.models.enums;

/**
 * What happened to a suggestion after it arrived. From the class diagram.
 *
 * <p>This is the project's "AI suggests, humans confirm" principle written as data. It is also the
 * measurement: at M4 the AI agreement rate is
 * {@code (AUTO_APPLIED + CONFIRMED) / everything reviewed}, which is only computable because the
 * outcome was recorded at the moment it was decided rather than inferred later.
 */
public enum ReviewStatus {

    /** It arrived, and its confidence was too low to apply on its own. Waiting for a person. */
    PENDING,

    /** Confidence was above the threshold, so the values were written onto the issue unattended. */
    AUTO_APPLIED,

    /** A person read it and agreed. */
    CONFIRMED,

    /** A person read it and disagreed. The issue keeps the human's values. */
    OVERRIDDEN
}
