package ma.dev.workflow.project.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.board.models.Board;
import ma.dev.workflow.board.models.enums.BoardType;
import ma.dev.workflow.board.repositories.BoardRepository;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.dto.ProjectDTO;
import ma.dev.workflow.project.dto.mapper.ProjectMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.events.ProjectDeletedEvent;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.project.service.IProjectService;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService implements IProjectService {

    private static final String DEFAULT_BOARD_NAME = "Main board";

    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;
    private final ProjectAccess projectAccess;
    private final JoinCodeGenerator joinCodeGenerator;
    private final ApplicationEventPublisher events;

    public ProjectService(ProjectRepository projectRepository,
                          BoardRepository boardRepository,
                          ProjectMemberRepository memberRepository,
                          UserRepository userRepository,
                          ProjectMapper projectMapper,
                          ProjectAccess projectAccess,
                          JoinCodeGenerator joinCodeGenerator,
                          ApplicationEventPublisher events) {
        this.projectRepository = projectRepository;
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.projectMapper = projectMapper;
        this.projectAccess = projectAccess;
        this.joinCodeGenerator = joinCodeGenerator;
        this.events = events;
    }

    /**
     * Only the projects the caller is on. An administrator sees all of them.
     *
     * <p>This is the list the whole feature hangs off, but on its own it is only cosmetic: hiding a
     * project here while {@code GET /issues/{id}} still answered would be a filter, not a rule.
     * That is why every other service checks membership too.
     */
    @Override
    public List<ProjectDTO> findAll() {
        if (projectAccess.isAdmin()) {
            return projectMapper.fromModelList(projectRepository.findAll());
        }
        return projectMapper.fromModelList(
                projectRepository.findAllById(projectAccess.myProjectIds()));
    }

    @Override
    public ProjectDTO findById(Long id) {
        projectAccess.requireMember(id);
        return projectMapper.fromModel(getOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectDTO create(ProjectDTO dto) {
        // A DEVELOPER joins projects; they do not start them. This is the only thing the global
        // MANAGER role means, and until M5 it meant nothing at all.
        projectAccess.requireCanCreateProjects();

        // This check produces a readable message in the normal case.
        // The unique constraint on project_key is what actually guarantees uniqueness:
        // two concurrent requests can both pass this check, and the database rejects the loser
        // with a DataIntegrityViolationException, handled as 409.
        if (projectRepository.existsByKey(dto.getKey())) {
            throw new BusinessRuleException("PROJECT_KEY_TAKEN",
                    "Project key already used: " + dto.getKey());
        }
        Project project = projectMapper.fromDTO(dto);
        project.setJoinCode(joinCodeGenerator.generate());
        Project saved = projectRepository.save(project);

        // The class diagram says a project has 1..* boards. SQL cannot express "at least one",
        // so the invariant is created here and protected by BoardService.deleteById.
        Board defaultBoard = new Board();
        defaultBoard.setName(DEFAULT_BOARD_NAME);
        defaultBoard.setType(BoardType.KANBAN);
        defaultBoard.setProject(saved);
        boardRepository.save(defaultBoard);

        // Whoever creates a project runs it. Nobody appoints the first project manager, because
        // there is nobody on the project yet to do the appointing - and a project that starts with
        // no manager could never gain one without an administrator stepping in every time.
        Long creatorId = projectAccess.currentUserId();
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + creatorId));
        ProjectMember membership = new ProjectMember();
        membership.setProject(saved);
        membership.setUser(creator);
        membership.setRole(ProjectRole.PROJECT_MANAGER);
        memberRepository.save(membership);

        return projectMapper.fromModel(saved);
    }

    @Override
    @Transactional
    public ProjectDTO update(Long id, ProjectDTO dto) {
        projectAccess.requireProjectManager(id);
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
        projectAccess.requireProjectManager(id);
        Project project = getOrThrow(id);
        projectRepository.delete(project);

        // The issues go with it through ON DELETE CASCADE, and that cascade is invisible outside
        // this database: IssueService.deleteById never runs, so no issue.deleted is ever published
        // and every vector for this project would be orphaned. One event for the whole cascade,
        // because the cascade is one act - a hundred deletions would be a hundred chances to lose
        // one.
        events.publishEvent(new ProjectDeletedEvent(project.getId(), project.getKey()));
    }

    private Project getOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
    }
}
