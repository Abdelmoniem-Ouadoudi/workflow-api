package ma.dev.workflow.issue.service;

import ma.dev.workflow.issue.dto.IssueDTO;
import ma.dev.workflow.issue.dto.IssueStatusUpdateDTO;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;

import java.util.List;

public interface IIssueService {

    List<IssueDTO> search(Long projectId, Long boardId, Long sprintId, Status status,
                          Priority priority, Long assigneeId, String text);

    IssueDTO findById(Long id);

    IssueDTO create(IssueDTO dto);

    IssueDTO update(Long id, IssueDTO dto);

    /** The Kanban drop. Rejects transitions the workflow does not allow. */
    IssueDTO updateStatus(Long id, IssueStatusUpdateDTO dto);

    /** Pass null to unassign. */
    IssueDTO assign(Long id, Long userId);

    void deleteById(Long id);
}
