package ma.dev.workflow.project.models.enums;

/**
 * Where a request to join a project has got to.
 *
 * <p>Settled rows are kept rather than deleted. Who asked and who said no is part of the record,
 * and it is what a second request from the same person has to be checked against.
 */
public enum JoinRequestStatus {

    /** Asked, nobody has decided. At most one of these per person per project. */
    PENDING,

    /** A project manager said yes. The membership row was created in the same transaction. */
    APPROVED,

    /** A project manager said no. The person may ask again. */
    REJECTED
}
