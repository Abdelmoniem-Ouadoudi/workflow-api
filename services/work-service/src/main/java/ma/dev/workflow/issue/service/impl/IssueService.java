package ma.dev.workflow.issue.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.board.models.Board;
import ma.dev.workflow.board.repositories.BoardRepository;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.CurrentUser;
import ma.dev.workflow.issue.dto.IssueDTO;
import ma.dev.workflow.issue.dto.IssueStatusUpdateDTO;
import ma.dev.workflow.issue.dto.mapper.IssueMapper;
import ma.dev.workflow.issue.events.IssueCreatedEvent;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.Priority;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.issue.service.IIssueService;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.sprint.models.Sprint;
import ma.dev.workflow.sprint.models.enums.SprintState;
import ma.dev.workflow.sprint.repositories.SprintRepository;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class IssueService implements IIssueService {

    /**
     * The workflow, declared as data rather than nested if statements.
     * Note what it forbids: TO_DO straight to DONE. Changing that rule is one line here.
     */
    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.TO_DO, EnumSet.of(Status.IN_PROGRESS),
            Status.IN_PROGRESS, EnumSet.of(Status.TO_DO, Status.DONE),
            Status.DONE, EnumSet.of(Status.IN_PROGRESS));

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final IssueMapper issueMapper;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    public IssueService(IssueRepository issueRepository,
                        ProjectRepository projectRepository,
                        BoardRepository boardRepository,
                        SprintRepository sprintRepository,
                        UserRepository userRepository,
                        IssueMapper issueMapper,
                        CurrentUser currentUser,
                        ApplicationEventPublisher events) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.boardRepository = boardRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.issueMapper = issueMapper;
        this.currentUser = currentUser;
        this.events = events;
    }

    @Override
    public List<IssueDTO> search(Long projectId, Long boardId, Long sprintId, Status status,
                                 Priority priority, Long assigneeId, String text) {
        return issueMapper.fromModelList(issueRepository.findAll(
                IssueSpecifications.filter(projectId, boardId, sprintId, status, priority, assigneeId, text)));
    }

    @Override
    public IssueDTO findById(Long id) {
        return issueMapper.fromModel(getOrThrow(id));
    }

    @Override
    @Transactional
    public IssueDTO create(IssueDTO dto) {
        // Locks the project row for the rest of this transaction so the counter cannot be
        // read twice with the same value by two concurrent creations.
        Project project = projectRepository.findByIdForUpdate(dto.getProjectId())
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND",
                        "Project not found: " + dto.getProjectId()));

        Issue issue = issueMapper.fromDTO(dto);
        issue.setProject(project);
        issue.setStatus(Status.TO_DO);
        // The reporter is whoever is holding the token, not whoever the request body claims.
        // Reading it from the body let any caller file an issue in someone else's name.
        issue.setReporter(requireUser(currentUser.requireId(), "REPORTER_NOT_FOUND"));

        if (dto.getAssigneeId() != null) {
            issue.setAssignee(requireUser(dto.getAssigneeId(), "ASSIGNEE_NOT_FOUND"));
        }
        if (dto.getBoardId() != null) {
            issue.setBoard(requireBoardInProject(dto.getBoardId(), project.getId()));
        }
        if (dto.getSprintId() != null) {
            issue.setSprint(requireSprintOnBoard(dto.getSprintId(), dto.getBoardId()));
        }

        long next = project.getIssueCounter() + 1;
        project.setIssueCounter(next);
        issue.setIssueKey(project.getKey() + "-" + next);

        Issue saved = issueRepository.saveAndFlush(issue);

        // Announce it, so the classifier can read the ticket. This is a Spring event, not a broker
        // call: IssueEventPublisher waits for the commit before anything reaches RabbitMQ, so a
        // consumer can never be handed an id that is not visible yet or that gets rolled back.
        // Keeping AMQP out of this class also means creating an issue does not wait on a broker.
        events.publishEvent(new IssueCreatedEvent(
                saved.getId(), saved.getIssueKey(), saved.getTitle(),
                saved.getDescription(), project.getKey()));

        return issueMapper.fromModel(saved);
    }

    @Override
    @Transactional
    public IssueDTO update(Long id, IssueDTO dto) {
        Issue issue = getOrThrow(id);

        issue.setTitle(dto.getTitle());
        issue.setDescription(dto.getDescription());
        issue.setType(dto.getType());
        issue.setPriority(dto.getPriority());
        issue.setDueDate(dto.getDueDate());

        // The project never changes: the issue key was built from it and is already public.
        issue.setBoard(dto.getBoardId() == null
                ? null
                : requireBoardInProject(dto.getBoardId(), issue.getProject().getId()));
        issue.setSprint(dto.getSprintId() == null
                ? null
                : requireSprintOnBoard(dto.getSprintId(), dto.getBoardId()));
        issue.setAssignee(dto.getAssigneeId() == null
                ? null
                : requireUser(dto.getAssigneeId(), "ASSIGNEE_NOT_FOUND"));

        return issueMapper.fromModel(issueRepository.saveAndFlush(issue));
    }

    @Override
    @Transactional
    public IssueDTO updateStatus(Long id, IssueStatusUpdateDTO dto) {
        Issue issue = getOrThrow(id);
        requireCurrentVersion(issue, dto.getVersion());

        Status current = issue.getStatus();
        Status target = dto.getStatus();

        if (current == target) {
            return issueMapper.fromModel(issue);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessRuleException("ILLEGAL_TRANSITION",
                    "Cannot move an issue from " + current + " to " + target + ".");
        }

        issue.setStatus(target);
        return issueMapper.fromModel(issueRepository.saveAndFlush(issue));
    }

    @Override
    @Transactional
    public IssueDTO assign(Long id, Long userId) {
        Issue issue = getOrThrow(id);
        issue.setAssignee(userId == null ? null : requireUser(userId, "ASSIGNEE_NOT_FOUND"));
        return issueMapper.fromModel(issueRepository.saveAndFlush(issue));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        issueRepository.delete(getOrThrow(id));
    }

    private Issue getOrThrow(Long id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + id));
    }

    private void requireCurrentVersion(Issue issue, Long clientVersion) {
        if (clientVersion != null && !clientVersion.equals(issue.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Issue " + issue.getIssueKey() + " was changed by someone else.");
        }
    }

    private User requireUser(Long userId, String code) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(code, "User not found: " + userId));
    }

    /** The schema cannot express this, so the service must: a board only holds its own project's issues. */
    private Board requireBoardInProject(Long boardId, Long projectId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessRuleException("BOARD_NOT_FOUND",
                        "Board not found: " + boardId));
        if (!board.getProject().getId().equals(projectId)) {
            throw new BusinessRuleException("BOARD_WRONG_PROJECT",
                    "Board " + boardId + " belongs to another project.");
        }
        return board;
    }

    private Sprint requireSprintOnBoard(Long sprintId, Long boardId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new BusinessRuleException("SPRINT_NOT_FOUND",
                        "Sprint not found: " + sprintId));
        if (boardId == null || !sprint.getBoard().getId().equals(boardId)) {
            throw new BusinessRuleException("SPRINT_WRONG_BOARD",
                    "Sprint " + sprintId + " does not belong to board " + boardId + ".");
        }
        if (sprint.getState() == SprintState.COMPLETED) {
            throw new BusinessRuleException("SPRINT_COMPLETED",
                    "Issues cannot be added to a completed sprint.");
        }
        return sprint;
    }
}
