package ma.dev.workflow.issue_comment.service;

import ma.dev.workflow.issue_comment.dto.IssueCommentDTO;

import java.util.List;

public interface IIssueCommentService {

    List<IssueCommentDTO> findByIssueId(Long issueId);

    IssueCommentDTO create(Long issueId, IssueCommentDTO dto);

    IssueCommentDTO update(Long issueId, Long commentId, IssueCommentDTO dto);

    void delete(Long issueId, Long commentId);
}
