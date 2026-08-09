package ma.dev.workflow.issue_attachment.service.impl;

import jakarta.persistence.EntityNotFoundException;
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

    public IssueAttachmentService(IssueAttachmentRepository attachmentRepository,
                                  IssueRepository issueRepository,
                                  IssueAttachmentMapper attachmentMapper) {
        this.attachmentRepository = attachmentRepository;
        this.issueRepository = issueRepository;
        this.attachmentMapper = attachmentMapper;
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
        IssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
        if (!attachment.getIssue().getId().equals(issueId)) {
            throw new EntityNotFoundException(
                    "Attachment " + attachmentId + " does not belong to issue " + issueId);
        }
        attachmentRepository.delete(attachment);
    }

    private Issue requireIssue(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
    }
}
