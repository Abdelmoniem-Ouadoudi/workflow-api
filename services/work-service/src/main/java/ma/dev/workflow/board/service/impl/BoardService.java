package ma.dev.workflow.board.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.board.dto.BoardDTO;
import ma.dev.workflow.board.dto.BoardViewDTO;
import ma.dev.workflow.board.dto.mapper.BoardMapper;
import ma.dev.workflow.board.models.Board;
import ma.dev.workflow.board.repositories.BoardRepository;
import ma.dev.workflow.board.service.IBoardService;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.issue.dto.IssueSummaryDTO;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.sprint.models.Sprint;
import ma.dev.workflow.sprint.models.enums.SprintState;
import ma.dev.workflow.sprint.repositories.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BoardService implements IBoardService {

    private final BoardRepository boardRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final BoardMapper boardMapper;
    private final ProjectAccess projectAccess;

    public BoardService(BoardRepository boardRepository,
                        ProjectRepository projectRepository,
                        SprintRepository sprintRepository,
                        IssueRepository issueRepository,
                        BoardMapper boardMapper,
                        ProjectAccess projectAccess) {
        this.boardRepository = boardRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
        this.boardMapper = boardMapper;
        this.projectAccess = projectAccess;
    }

    @Override
    public BoardViewDTO getView(Long boardId) {
        Board board = getVisibleOrThrow(boardId);
        Sprint active = sprintRepository
                .findByBoardIdAndState(boardId, SprintState.ACTIVE)
                .orElse(null);

        // One switch, in one place. Spreading "if type == SCRUM" across methods is how
        // the two board types drift apart.
        List<IssueSummaryDTO> issues = switch (board.getType()) {
            case KANBAN -> issueRepository.findBoardSummaries(boardId);
            // No active sprint is not an error: the columns are simply empty.
            case SCRUM -> active == null
                    ? List.of()
                    : issueRepository.findSprintSummaries(boardId, active.getId());
        };

        Map<Status, List<IssueSummaryDTO>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(IssueSummaryDTO::status));

        List<BoardViewDTO.Column> columns = Arrays.stream(Status.values())
                .map(status -> new BoardViewDTO.Column(
                        status, byPriority(byStatus.getOrDefault(status, List.of()))))
                .toList();

        BoardViewDTO.ActiveSprint activeSprint = active == null
                ? null
                : new BoardViewDTO.ActiveSprint(active.getId(), active.getName(), active.getGoal());

        return new BoardViewDTO(board.getId(), board.getName(), board.getType(), activeSprint, columns);
    }

    @Override
    public List<IssueSummaryDTO> getBacklog(Long boardId) {
        getVisibleOrThrow(boardId);
        return byPriority(issueRepository.findBacklogSummaries(boardId));
    }

    /**
     * Most urgent first, oldest first within the same priority.
     * Done in Java, not SQL: the priority column is a varchar, so ORDER BY would sort
     * alphabetically and put LOW above HIGH. Sort is stable, so the query's createdAt
     * ordering survives as the tie-break.
     */
    private List<IssueSummaryDTO> byPriority(List<IssueSummaryDTO> issues) {
        return issues.stream()
                .sorted(Comparator.comparingInt(issue -> issue.priority().getRank()))
                .toList();
    }

    @Override
    public List<BoardDTO> findAll() {
        if (projectAccess.isAdmin()) {
            return boardMapper.fromModelList(boardRepository.findAll());
        }
        return boardMapper.fromModelList(
                boardRepository.findByProjectIdIn(projectAccess.myProjectIds()));
    }

    @Override
    public List<BoardDTO> findByProjectId(Long projectId) {
        projectAccess.requireMember(projectId);
        requireProject(projectId);
        return boardMapper.fromModelList(boardRepository.findByProjectId(projectId));
    }

    @Override
    public BoardDTO findById(Long id) {
        return boardMapper.fromModel(getVisibleOrThrow(id));
    }

    @Override
    @Transactional
    public BoardDTO create(BoardDTO dto) {
        // Boards are project structure, not content: adding or removing one changes what every
        // member of the project sees. That is the project manager's call, not any member's.
        projectAccess.requireProjectManager(dto.getProjectId());
        Project project = requireProject(dto.getProjectId());
        Board board = boardMapper.fromDTO(dto);
        board.setProject(project);
        return boardMapper.fromModel(boardRepository.save(board));
    }

    @Override
    @Transactional
    public BoardDTO update(Long id, BoardDTO dto) {
        Board board = getOrThrow(id);
        projectAccess.requireProjectManager(board.getProject().getId());
        // A board does not move between projects: its issues belong to the original project.
        board.setName(dto.getName());
        board.setType(dto.getType());
        return boardMapper.fromModel(boardRepository.save(board));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Board board = getOrThrow(id);
        // The class diagram requires at least one board per project (1..*).
        Long projectId = board.getProject().getId();
        projectAccess.requireProjectManager(projectId);
        if (boardRepository.findByProjectId(projectId).size() <= 1) {
            throw new BusinessRuleException("LAST_BOARD",
                    "A project must keep at least one board.");
        }
        boardRepository.delete(board);
    }

    private Board getOrThrow(Long id) {
        return boardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Board not found: " + id));
    }

    /**
     * Load the board, then check the caller is on the project it turns out to belong to.
     *
     * <p>This ordering is why the check cannot be a path rule or a {@code @PreAuthorize}: the URL
     * carries a board id, and which project that board belongs to is only knowable after the row
     * has been read.
     */
    private Board getVisibleOrThrow(Long id) {
        Board board = getOrThrow(id);
        projectAccess.requireMember(board.getProject().getId());
        return board;
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND",
                        "Project not found: " + projectId));
    }
}
