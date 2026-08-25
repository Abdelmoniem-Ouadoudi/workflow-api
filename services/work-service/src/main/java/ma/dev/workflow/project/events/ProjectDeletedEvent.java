package ma.dev.workflow.project.events;

/**
 * A project was deleted, and its issues went with it.
 *
 * <p>Exists because a database cascade is invisible to another service. `issue` has
 * {@code ON DELETE CASCADE} on `project_id`, so deleting a project removes every issue in it
 * without {@code IssueService.deleteById} ever running — and therefore without a single
 * {@code issue.deleted} being published. The vectors for those issues would be orphaned, and the
 * duplicate panel would go on offering tickets from a project that no longer exists.
 *
 * <p>One message rather than one per issue. The cascade is a single act, and modelling it as a
 * hundred deletions would be a hundred chances to lose one.
 *
 * <p>Carries the key rather than the id because that is the metadata the vectors are tagged with.
 */
public record ProjectDeletedEvent(
        Long projectId,
        String projectKey
) {
}
