package ma.dev.workflow.issue.events;

/**
 * What crosses the broker when an issue is created.
 *
 * <p>Deliberately not {@code IssueDTO}. A DTO exists to serve the React app and changes whenever a
 * screen changes; this is a contract with another service. Sending the DTO would mean a field
 * added for a form quietly becomes part of an integration nobody re-read.
 *
 * <p>It carries only what a classifier needs to read a ticket: what it is called and what it says.
 * No reporter, no board, no status — none of that helps decide whether something is a bug.
 */
public record IssueCreatedEvent(
        Long issueId,
        String issueKey,
        String title,
        String description,
        String projectKey
) {
}
