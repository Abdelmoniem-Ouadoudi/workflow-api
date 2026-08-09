package ma.dev.workflow.issue.service.impl;

import jakarta.persistence.criteria.Predicate;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Composable filters for GET /issues.
 * One method per filter beats fifteen findByAAndBAndC repository methods,
 * and unlike string-built JPQL it cannot be injected into.
 */
final class IssueSpecifications {

    private IssueSpecifications() {
    }

    static Specification<Issue> filter(Long projectId, Long boardId, Long sprintId, Status status,
                                       Priority priority, Long assigneeId, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (boardId != null) {
                predicates.add(cb.equal(root.get("board").get("id"), boardId));
            }
            if (sprintId != null) {
                predicates.add(cb.equal(root.get("sprint").get("id"), sprintId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("issueKey")), pattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
