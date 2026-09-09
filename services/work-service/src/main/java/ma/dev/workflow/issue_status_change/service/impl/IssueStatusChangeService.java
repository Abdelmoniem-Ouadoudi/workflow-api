package ma.dev.workflow.issue_status_change.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.issue_status_change.dto.IssueStatusChangeDTO;
import ma.dev.workflow.issue_status_change.dto.mapper.IssueStatusChangeMapper;
import ma.dev.workflow.issue_status_change.repositories.IssueStatusChangeRepository;
import ma.dev.workflow.issue_status_change.service.IIssueStatusChangeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class IssueStatusChangeService implements IIssueStatusChangeService {

    private final IssueStatusChangeRepository historyRepository;
    private final IssueRepository issueRepository;
    private final IssueStatusChangeMapper historyMapper;
    private final ProjectAccess projectAccess;

    public IssueStatusChangeService(IssueStatusChangeRepository historyRepository,
                                    IssueRepository issueRepository,
                                    IssueStatusChangeMapper historyMapper,
                                    ProjectAccess projectAccess) {
        this.historyRepository = historyRepository;
        this.issueRepository = issueRepository;
        this.historyMapper = historyMapper;
        this.projectAccess = projectAccess;
    }

    @Override
    public List<IssueStatusChangeDTO> findByIssueId(Long issueId) {
        // The same rule as comments: history is reachable only through its issue, and an issue
        // only by a member of its project. Without this, /issues/7/history would say who works
        // on a project you cannot open, and when.
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
        projectAccess.requireMember(issue.getProject().getId());

        return historyMapper.fromModelList(
                historyRepository.findByIssueIdOrderByChangedAtAsc(issueId));
    }
}
