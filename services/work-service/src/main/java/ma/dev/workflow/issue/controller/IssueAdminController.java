package ma.dev.workflow.issue.controller;

import ma.dev.workflow.issue.service.IIssueService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational actions on issues, as opposed to working with them.
 *
 * <p>Separate from {@code IssueController} because the audience is different: this is for whoever
 * runs the system, and it is ADMIN-only in the security chain. Mixing it into the resource
 * controller would put an operator's button next to a user's.
 */
@RestController
@RequestMapping("/admin/issues")
public class IssueAdminController {

    private final IIssueService issueService;

    public IssueAdminController(IIssueService issueService) {
        this.issueService = issueService;
    }

    /**
     * Re-reads every issue through the classification pipeline.
     *
     * <p>Needed after the embedding model changes, and once after M4 for the issues that were
     * created before there was anything to embed them with. Returns immediately with a count: the
     * actual work happens on the other side of the queue, and holding the request open while a
     * model reads a few hundred tickets would only produce a timeout.
     */
    @PostMapping("/reindex")
    public ReindexResult reindex() {
        return new ReindexResult(issueService.reindexAll());
    }

    /** {@code queued}, not {@code done} — the naming is the honest part. */
    public record ReindexResult(int queued) {
    }
}
