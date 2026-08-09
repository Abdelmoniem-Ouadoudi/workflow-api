package ma.dev.workflow.issue_attachment.service;

import ma.dev.workflow.issue_attachment.dto.IssueAttachmentDTO;

import java.util.List;

public interface IIssueAttachmentService {

    List<IssueAttachmentDTO> findByIssueId(Long issueId);

    IssueAttachmentDTO create(Long issueId, IssueAttachmentDTO dto);

    void delete(Long issueId, Long attachmentId);
}
