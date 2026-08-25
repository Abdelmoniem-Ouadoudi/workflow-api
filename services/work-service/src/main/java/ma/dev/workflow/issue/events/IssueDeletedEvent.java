package ma.dev.workflow.issue.events;

/**
 * An issue was deleted.
 *
 * <p>Carries the id because that is what the classifier deletes by, and the key because that is
 * what a person reads in a log. Nothing else: this event asks another service to forget something,
 * not to read it.
 */
public record IssueDeletedEvent(
        Long issueId,
        String issueKey
) {
}
