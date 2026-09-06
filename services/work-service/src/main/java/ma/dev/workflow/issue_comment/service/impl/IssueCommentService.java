package ma.dev.workflow.issue_comment.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.CurrentUser;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.issue_comment.dto.IssueCommentDTO;
import ma.dev.workflow.issue_comment.dto.mapper.IssueCommentMapper;
import ma.dev.workflow.issue_comment.models.IssueComment;
import ma.dev.workflow.issue_comment.repositories.IssueCommentRepository;
import ma.dev.workflow.issue_comment.service.IIssueCommentService;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class IssueCommentService implements IIssueCommentService {

    private final IssueCommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final IssueCommentMapper commentMapper;
    private final CurrentUser currentUser;
    private final ProjectAccess projectAccess;

    public IssueCommentService(IssueCommentRepository commentRepository,
                               IssueRepository issueRepository,
                               UserRepository userRepository,
                               IssueCommentMapper commentMapper,
                               CurrentUser currentUser,
                               ProjectAccess projectAccess) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.commentMapper = commentMapper;
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
    }

    @Override
    public List<IssueCommentDTO> findByIssueId(Long issueId) {
        requireIssue(issueId);
        return commentMapper.fromModelList(commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId));
    }

    @Override
    @Transactional
    public IssueCommentDTO create(Long issueId, IssueCommentDTO dto) {
        Issue issue = requireIssue(issueId);
        // The author is whoever holds the token. Reading it from the body let any caller post a
        // comment under someone else's name.
        Long authorId = currentUser.requireId();
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessRuleException("AUTHOR_NOT_FOUND",
                        "User not found: " + authorId));

        IssueComment comment = new IssueComment();
        comment.setContent(dto.getContent());
        comment.setIssue(issue);
        comment.setAuthor(author);
        return commentMapper.fromModel(commentRepository.saveAndFlush(comment));
    }

    @Override
    @Transactional
    public IssueCommentDTO update(Long issueId, Long commentId, IssueCommentDTO dto) {
        IssueComment comment = requireCommentOnIssue(issueId, commentId);
        // Only the text changes. The author and the issue are fixed once written.
        comment.setContent(dto.getContent());
        return commentMapper.fromModel(commentRepository.saveAndFlush(comment));
    }

    @Override
    @Transactional
    public void delete(Long issueId, Long commentId) {
        commentRepository.delete(requireCommentOnIssue(issueId, commentId));
    }

    /**
     * Every path in this service goes through here, which is why the membership check lives here
     * rather than being repeated in five methods. A comment is only reachable through its issue,
     * and an issue is only reachable by a member of its project.
     */
    private Issue requireIssue(Long issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
        projectAccess.requireMember(issue.getProject().getId());
        return issue;
    }

    /** Guards against /issues/5/comments/9 where comment 9 belongs to issue 7. */
    private IssueComment requireCommentOnIssue(Long issueId, Long commentId) {
        requireIssue(issueId);
        IssueComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
        if (!comment.getIssue().getId().equals(issueId)) {
            throw new EntityNotFoundException(
                    "Comment " + commentId + " does not belong to issue " + issueId);
        }
        return comment;
    }
}
