package ma.dev.workflow.issue_attachment.repositories;

import ma.dev.workflow.issue_attachment.models.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {

    List<IssueAttachment> findByIssueIdOrderByUploadedAtAsc(Long issueId);
}
