package ma.dev.workflow.issue_attachment.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.issue_attachment.dto.IssueAttachmentDTO;
import ma.dev.workflow.issue_attachment.service.IIssueAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Attachment metadata only. Uploading the bytes over multipart is listed in docs/BACKLOG.md.
 * An attachment is never updated: replace it by deleting and posting a new one.
 */
@RestController
@RequestMapping("/issues/{issueId}/attachments")
public class IssueAttachmentController {

    private final IIssueAttachmentService attachmentService;

    public IssueAttachmentController(IIssueAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<IssueAttachmentDTO> findByIssue(@PathVariable Long issueId) {
        return attachmentService.findByIssueId(issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueAttachmentDTO create(@PathVariable Long issueId,
                                     @Valid @RequestBody IssueAttachmentDTO dto) {
        return attachmentService.create(issueId, dto);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long issueId, @PathVariable Long attachmentId) {
        attachmentService.delete(issueId, attachmentId);
    }
}
