package ma.dev.workflow.issue.dto;

import ma.dev.workflow.issue.models.enums.IssueType;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;

import java.time.LocalDate;

/**
 * What a Kanban card shows. Built by a constructor projection straight from the query,
 * so the Issue entities are never loaded and the assignee name costs no extra query.
 */
public record IssueSummaryDTO(
        Long id,
        String issueKey,
        String title,
        IssueType type,
        Status status,
        Priority priority,
        Long assigneeId,
        String assigneeUsername,
        LocalDate dueDate,
        Long version) {
}
