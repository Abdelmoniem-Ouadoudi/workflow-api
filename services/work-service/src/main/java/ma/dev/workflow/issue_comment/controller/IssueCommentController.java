package ma.dev.workflow.issue_comment.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.issue_comment.dto.IssueCommentDTO;
import ma.dev.workflow.issue_comment.service.IIssueCommentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A comment has no life of its own, so it lives under its issue.
 * A plain controller rather than a shared CRUD base class: a base class assumes one flat
 * path with a single {id}, which does not fit a nested resource.
 */
@RestController
@RequestMapping("/issues/{issueId}/comments")
public class IssueCommentController {

    private final IIssueCommentService commentService;

    public IssueCommentController(IIssueCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<IssueCommentDTO> findByIssue(@PathVariable Long issueId) {
        return commentService.findByIssueId(issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueCommentDTO create(@PathVariable Long issueId,
                                  @Valid @RequestBody IssueCommentDTO dto) {
        return commentService.create(issueId, dto);
    }

    @PutMapping("/{commentId}")
    public IssueCommentDTO update(@PathVariable Long issueId,
                                  @PathVariable Long commentId,
                                  @Valid @RequestBody IssueCommentDTO dto) {
        return commentService.update(issueId, commentId, dto);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long issueId, @PathVariable Long commentId) {
        commentService.delete(issueId, commentId);
    }
}
