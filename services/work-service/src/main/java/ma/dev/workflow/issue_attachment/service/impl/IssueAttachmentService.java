package ma.dev.workflow.issue_attachment.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.issue_attachment.dto.IssueAttachmentDTO;
import ma.dev.workflow.issue_attachment.dto.mapper.IssueAttachmentMapper;
import ma.dev.workflow.issue_attachment.models.IssueAttachment;
import ma.dev.workflow.issue_attachment.repositories.IssueAttachmentRepository;
import ma.dev.workflow.issue_attachment.service.IIssueAttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class IssueAttachmentService implements IIssueAttachmentService {

    private final IssueAttachmentRepository attachmentRepository;
    private final IssueRepository issueRepository;
    private final IssueAttachmentMapper attachmentMapper;
    private final ProjectAccess projectAccess;

    public IssueAttachmentService(IssueAttachmentRepository attachmentRepository,
                                  IssueRepository issueRepository,
                                  IssueAttachmentMapper attachmentMapper,
                                  ProjectAccess projectAccess) {
        this.attachmentRepository = attachmentRepository;
        this.issueRepository = issueRepository;
        this.attachmentMapper = attachmentMapper;
        this.projectAccess = projectAccess;
    }

    @Override
    public List<IssueAttachmentDTO> findByIssueId(Long issueId) {
        requireIssue(issueId);
        return attachmentMapper.fromModelList(
                attachmentRepository.findByIssueIdOrderByUploadedAtAsc(issueId));
    }

    @Override
    @Transactional
    public IssueAttachmentDTO create(Long issueId, IssueAttachmentDTO dto) {
        Issue issue = requireIssue(issueId);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setFileName(dto.getFileName());
        attachment.setFileUrl(dto.getFileUrl());
        attachment.setFileSize(dto.getFileSize());
        attachment.setIssue(issue);
        return attachmentMapper.fromModel(attachmentRepository.saveAndFlush(attachment));
    }

    @Override
    @Transactional
    public void delete(Long issueId, Long attachmentId) {
        requireIssue(issueId);
        IssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
        if (!attachment.getIssue().getId().equals(issueId)) {
            throw new EntityNotFoundException(
                    "Attachment " + attachmentId + " does not belong to issue " + issueId);
        }
        attachmentRepository.delete(attachment);
    }

    /** Every method here goes through this, so the membership check is stated once. */
    private Issue requireIssue(Long issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
        projectAccess.requireMember(issue.getProject().getId());
        return issue;
    }
}
