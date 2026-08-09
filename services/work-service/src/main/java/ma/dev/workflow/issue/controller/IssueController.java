package ma.dev.workflow.issue.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.issue.dto.IssueDTO;
import ma.dev.workflow.issue.dto.IssueStatusUpdateDTO;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.service.IIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/issues")
public class IssueController {

    private final IIssueService issueService;

    public IssueController(IIssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    public List<IssueDTO> search(@RequestParam(required = false) Long projectId,
                                 @RequestParam(required = false) Long boardId,
                                 @RequestParam(required = false) Long sprintId,
                                 @RequestParam(required = false) Status status,
                                 @RequestParam(required = false) Priority priority,
                                 @RequestParam(required = false) Long assigneeId,
                                 @RequestParam(required = false) String q) {
        return issueService.search(projectId, boardId, sprintId, status, priority, assigneeId, q);
    }

    @GetMapping("/{id}")
    public IssueDTO findById(@PathVariable Long id) {
        return issueService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueDTO create(@Valid @RequestBody IssueDTO dto) {
        return issueService.create(dto);
    }

    @PutMapping("/{id}")
    public IssueDTO update(@PathVariable Long id, @Valid @RequestBody IssueDTO dto) {
        return issueService.update(id, dto);
    }

    /** What the Kanban board calls when a card is dropped in another column. */
    @PatchMapping("/{id}/status")
    public IssueDTO updateStatus(@PathVariable Long id,
                                 @Valid @RequestBody IssueStatusUpdateDTO dto) {
        return issueService.updateStatus(id, dto);
    }

    @PutMapping("/{id}/assignee")
    public IssueDTO assign(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return issueService.assign(id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable Long id) {
        issueService.deleteById(id);
    }
}
