package ma.dev.workflow.issue_status_change.controller;

import ma.dev.workflow.issue_status_change.dto.IssueStatusChangeDTO;
import ma.dev.workflow.issue_status_change.service.IIssueStatusChangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The history of one issue, nested under it like its comments.
 *
 * <p>GET only, and that is the whole feature: the rows are written by moving a card, so a POST
 * here would let a caller invent a move that never happened, and a DELETE would let them erase
 * one that did.
 */
@RestController
@RequestMapping("/issues/{issueId}/history")
public class IssueStatusChangeController {

    private final IIssueStatusChangeService historyService;

    public IssueStatusChangeController(IIssueStatusChangeService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<IssueStatusChangeDTO> findByIssue(@PathVariable Long issueId) {
        return historyService.findByIssueId(issueId);
    }
}
