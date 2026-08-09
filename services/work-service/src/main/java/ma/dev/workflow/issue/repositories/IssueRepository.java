package ma.dev.workflow.issue.repositories;

import ma.dev.workflow.issue.dto.IssueSummaryDTO;
import ma.dev.workflow.issue.models.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    List<Issue> findBySprintId(Long sprintId);

    boolean existsByAssigneeId(Long assigneeId);

    boolean existsByReporterId(Long reporterId);

    /**
     * Constructor projection: builds the DTO inside the query, so no Issue entity is created
     * and the assignee name arrives in the same round trip. A 200-issue board is one query,
     * not 201.
     * LEFT JOIN, not the implicit join of "i.assignee.username": an inner join would silently
     * drop every unassigned issue from the board.
     */
    @Query("""
            select new ma.dev.workflow.issue.dto.IssueSummaryDTO(
                i.id, i.issueKey, i.title, i.type, i.status, i.priority,
                a.id, a.username, i.dueDate, i.version)
            from Issue i
            left join i.assignee a
            where i.board.id = :boardId
            order by i.createdAt asc
            """)
    List<IssueSummaryDTO> findBoardSummaries(@Param("boardId") Long boardId);

    @Query("""
            select new ma.dev.workflow.issue.dto.IssueSummaryDTO(
                i.id, i.issueKey, i.title, i.type, i.status, i.priority,
                a.id, a.username, i.dueDate, i.version)
            from Issue i
            left join i.assignee a
            where i.board.id = :boardId and i.sprint.id = :sprintId
            order by i.createdAt asc
            """)
    List<IssueSummaryDTO> findSprintSummaries(@Param("boardId") Long boardId,
                                              @Param("sprintId") Long sprintId);

    /** A null sprint is the backlog. There is no Backlog entity. */
    @Query("""
            select new ma.dev.workflow.issue.dto.IssueSummaryDTO(
                i.id, i.issueKey, i.title, i.type, i.status, i.priority,
                a.id, a.username, i.dueDate, i.version)
            from Issue i
            left join i.assignee a
            where i.board.id = :boardId and i.sprint is null
            order by i.createdAt asc
            """)
    List<IssueSummaryDTO> findBacklogSummaries(@Param("boardId") Long boardId);
}
