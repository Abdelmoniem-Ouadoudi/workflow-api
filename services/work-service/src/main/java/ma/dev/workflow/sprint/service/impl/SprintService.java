package ma.dev.workflow.sprint.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.board.models.Board;
import ma.dev.workflow.board.repositories.BoardRepository;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.repositories.IssueRepository;
import ma.dev.workflow.sprint.dto.SprintDTO;
import ma.dev.workflow.sprint.dto.mapper.SprintMapper;
import ma.dev.workflow.sprint.models.Sprint;
import ma.dev.workflow.sprint.models.enums.SprintState;
import ma.dev.workflow.sprint.repositories.SprintRepository;
import ma.dev.workflow.sprint.service.ISprintService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SprintService implements ISprintService {

    private final SprintRepository sprintRepository;
    private final BoardRepository boardRepository;
    private final IssueRepository issueRepository;
    private final SprintMapper sprintMapper;

    public SprintService(SprintRepository sprintRepository,
                         BoardRepository boardRepository,
                         IssueRepository issueRepository,
                         SprintMapper sprintMapper) {
        this.sprintRepository = sprintRepository;
        this.boardRepository = boardRepository;
        this.issueRepository = issueRepository;
        this.sprintMapper = sprintMapper;
    }

    @Override
    public List<SprintDTO> findAll() {
        return sprintMapper.fromModelList(sprintRepository.findAll());
    }

    @Override
    public List<SprintDTO> findByBoardId(Long boardId) {
        requireBoard(boardId);
        return sprintMapper.fromModelList(sprintRepository.findByBoardId(boardId));
    }

    @Override
    public SprintDTO findById(Long id) {
        return sprintMapper.fromModel(getOrThrow(id));
    }

    @Override
    @Transactional
    public SprintDTO create(SprintDTO dto) {
        Board board = requireBoard(dto.getBoardId());
        requireValidDates(dto.getStartDate(), dto.getEndDate());

        Sprint sprint = sprintMapper.fromDTO(dto);
        sprint.setBoard(board);
        sprint.setState(SprintState.PLANNED);
        return sprintMapper.fromModel(sprintRepository.save(sprint));
    }

    @Override
    @Transactional
    public SprintDTO update(Long id, SprintDTO dto) {
        Sprint sprint = getOrThrow(id);
        if (sprint.getState() == SprintState.COMPLETED) {
            throw new BusinessRuleException("SPRINT_COMPLETED",
                    "A completed sprint cannot be modified.");
        }
        requireValidDates(dto.getStartDate(), dto.getEndDate());

        sprint.setName(dto.getName());
        sprint.setGoal(dto.getGoal());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        return sprintMapper.fromModel(sprintRepository.save(sprint));
    }

    @Override
    @Transactional
    public SprintDTO start(Long id) {
        Sprint sprint = getOrThrow(id);
        if (sprint.getState() != SprintState.PLANNED) {
            throw new BusinessRuleException("SPRINT_NOT_PLANNED",
                    "Only a planned sprint can be started. This one is " + sprint.getState() + ".");
        }
        // This check gives a clear message. The partial unique index
        // uk_sprint_one_active_per_board is what actually guarantees the rule:
        // two concurrent starts would both read "none active" and both write.
        Long boardId = sprint.getBoard().getId();
        sprintRepository.findByBoardIdAndState(boardId, SprintState.ACTIVE)
                .ifPresent(active -> {
                    throw new BusinessRuleException("SPRINT_ALREADY_ACTIVE",
                            "Sprint " + active.getName() + " is already active on this board.");
                });

        sprint.setState(SprintState.ACTIVE);
        return sprintMapper.fromModel(sprintRepository.saveAndFlush(sprint));
    }

    @Override
    @Transactional
    public SprintDTO complete(Long id) {
        Sprint sprint = getOrThrow(id);
        if (sprint.getState() != SprintState.ACTIVE) {
            throw new BusinessRuleException("SPRINT_NOT_ACTIVE",
                    "Only an active sprint can be completed. This one is " + sprint.getState() + ".");
        }

        // Unfinished work returns to the backlog. Moving it to the next sprint would need an
        // ordering between sprints that the model does not define.
        // Both writes are in one transaction: either the sprint closes and the issues move, or neither.
        List<Issue> unfinished = issueRepository.findBySprintId(id).stream()
                .filter(issue -> issue.getStatus() != Status.DONE)
                .toList();
        unfinished.forEach(issue -> issue.setSprint(null));
        issueRepository.saveAll(unfinished);

        sprint.setState(SprintState.COMPLETED);
        return sprintMapper.fromModel(sprintRepository.saveAndFlush(sprint));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Sprint sprint = getOrThrow(id);
        if (sprint.getState() == SprintState.ACTIVE) {
            throw new BusinessRuleException("SPRINT_ACTIVE",
                    "An active sprint cannot be deleted. Complete it first.");
        }
        sprintRepository.delete(sprint);
    }

    private Sprint getOrThrow(Long id) {
        return sprintRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sprint not found: " + id));
    }

    private Board requireBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessRuleException("BOARD_NOT_FOUND",
                        "Board not found: " + boardId));
    }

    private void requireValidDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("INVALID_SPRINT_DATES",
                    "End date cannot be before start date.");
        }
    }
}
