package ma.dev.workflow.issue_status_change.service;

import ma.dev.workflow.issue_status_change.dto.IssueStatusChangeDTO;

import java.util.List;

/**
 * Reading the history. There is deliberately no write method here.
 *
 * <p>Rows are written by {@code IssueService.updateStatus}, in the same transaction as the move,
 * because a history that can be written on its own is a history that can disagree with the board.
 */
public interface IIssueStatusChangeService {

    /** Oldest move first. Empty for an issue nobody has moved yet — that is not an error. */
    List<IssueStatusChangeDTO> findByIssueId(Long issueId);
}
