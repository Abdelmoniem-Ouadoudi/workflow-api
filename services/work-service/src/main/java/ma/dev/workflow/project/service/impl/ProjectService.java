package ma.dev.workflow.project.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.board.models.Board;
import ma.dev.workflow.board.models.enums.BoardType;
import ma.dev.workflow.board.repositories.BoardRepository;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.project.dto.ProjectDTO;
import ma.dev.workflow.project.dto.mapper.ProjectMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.project.service.IProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService implements IProjectService {

    private static final String DEFAULT_BOARD_NAME = "Main board";

    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          BoardRepository boardRepository,
                          ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.boardRepository = boardRepository;
        this.projectMapper = projectMapper;
    }

    @Override
    public List<ProjectDTO> findAll() {
        return projectMapper.fromModelList(projectRepository.findAll());
    }

    @Override
    public ProjectDTO findById(Long id) {
        return projectMapper.fromModel(getOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectDTO create(ProjectDTO dto) {
        // This check produces a readable message in the normal case.
        // The unique constraint on project_key is what actually guarantees uniqueness:
        // two concurrent requests can both pass this check, and the database rejects the loser
        // with a DataIntegrityViolationException, handled as 409.
        if (projectRepository.existsByKey(dto.getKey())) {
            throw new BusinessRuleException("PROJECT_KEY_TAKEN",
                    "Project key already used: " + dto.getKey());
        }
        Project project = projectMapper.fromDTO(dto);
        Project saved = projectRepository.save(project);

        // The class diagram says a project has 1..* boards. SQL cannot express "at least one",
        // so the invariant is created here and protected by BoardService.deleteById.
        Board defaultBoard = new Board();
        defaultBoard.setName(DEFAULT_BOARD_NAME);
        defaultBoard.setType(BoardType.KANBAN);
        defaultBoard.setProject(saved);
        boardRepository.save(defaultBoard);

        return projectMapper.fromModel(saved);
    }

    @Override
    @Transactional
    public ProjectDTO update(Long id, ProjectDTO dto) {
        Project project = getOrThrow(id);
        // The key is the public identifier of the project. Changing it would break
        // every issue key already printed in the UI, so it is not updatable.
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        return projectMapper.fromModel(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        projectRepository.delete(getOrThrow(id));
    }

    private Project getOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
    }
}
