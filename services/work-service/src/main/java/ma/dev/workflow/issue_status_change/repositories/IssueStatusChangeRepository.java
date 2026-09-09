package ma.dev.workflow.issue_status_change.repositories;

import ma.dev.workflow.issue_status_change.models.IssueStatusChange;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueStatusChangeRepository extends JpaRepository<IssueStatusChange, Long> {

    /**
     * Oldest first, so the list reads as the story of the card rather than in reverse.
     *
     * <p>The entity graph is the point of the method: every row is rendered with the name of the
     * person who made the move, and a lazy {@code changedBy} would fetch that name one query per
     * row. One join instead of N+1.
     */
    @EntityGraph(attributePaths = "changedBy")
    List<IssueStatusChange> findByIssueIdOrderByChangedAtAsc(Long issueId);
}
