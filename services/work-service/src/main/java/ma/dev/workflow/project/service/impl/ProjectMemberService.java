package ma.dev.workflow.project.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.dto.AddMemberRequest;
import ma.dev.workflow.project.dto.ProjectMemberDTO;
import ma.dev.workflow.project.dto.mapper.ProjectMemberMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.project.service.IProjectMemberService;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectMemberService implements IProjectMemberService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper memberMapper;
    private final ProjectAccess projectAccess;
    private final JoinCodeGenerator joinCodeGenerator;

    public ProjectMemberService(ProjectRepository projectRepository,
                                ProjectMemberRepository memberRepository,
                                UserRepository userRepository,
                                ProjectMemberMapper memberMapper,
                                ProjectAccess projectAccess,
                                JoinCodeGenerator joinCodeGenerator) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.memberMapper = memberMapper;
        this.projectAccess = projectAccess;
        this.joinCodeGenerator = joinCodeGenerator;
    }

    /**
     * Any member may see who else is on the project. This is also what the board's assignee
     * dropdown reads, which is why it is not restricted to the project manager: before M5 that
     * dropdown called {@code GET /users} and handed every user's email to anybody signed in.
     */
    @Override
    public List<ProjectMemberDTO> findByProject(Long projectId) {
        projectAccess.requireMember(projectId);
        requireProject(projectId);
        return memberMapper.fromModelList(memberRepository.findByProjectId(projectId));
    }

    @Override
    @Transactional
    public ProjectMemberDTO add(Long projectId, AddMemberRequest request) {
        projectAccess.requireProjectManager(projectId);
        Project project = requireProject(projectId);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.userId()));

        // A deactivated or not-yet-approved account cannot be put on a project. Otherwise a
        // project manager could quietly give work to somebody an administrator has switched off.
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BusinessRuleException("USER_NOT_ACTIVE",
                    "This account is not active and cannot be added to a project.");
        }

        // Readable message in the normal case; uq_project_member is what actually guarantees it
        // when two managers add the same person at the same moment.
        if (memberRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new BusinessRuleException("ALREADY_A_MEMBER",
                    "This person is already on the project.");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(request.role());
        return memberMapper.fromModel(memberRepository.save(member));
    }

    @Override
    @Transactional
    public ProjectMemberDTO changeRole(Long projectId, Long userId, ProjectRole role) {
        projectAccess.requireProjectManager(projectId);
        ProjectMember member = requireMembership(projectId, userId);

        if (member.getRole() != role) {
            requireNotTheLastProjectManager(member, "demote");
        }
        member.setRole(role);
        return memberMapper.fromModel(memberRepository.save(member));
    }

    @Override
    @Transactional
    public void remove(Long projectId, Long userId) {
        projectAccess.requireProjectManager(projectId);
        ProjectMember member = requireMembership(projectId, userId);
        requireNotTheLastProjectManager(member, "remove");
        memberRepository.delete(member);
    }

    @Override
    public String joinCode(Long projectId) {
        projectAccess.requireProjectManager(projectId);
        return requireProject(projectId).getJoinCode();
    }

    /**
     * A new code. Nothing that already exists is affected: current members stay members and open
     * requests stay open. Rotating only stops the <em>next</em> person from using a code that has
     * been forwarded further than it should have been.
     */
    @Override
    @Transactional
    public String rotateJoinCode(Long projectId) {
        projectAccess.requireProjectManager(projectId);
        Project project = requireProject(projectId);
        project.setJoinCode(joinCodeGenerator.generate());
        return projectRepository.save(project).getJoinCode();
    }

    /**
     * A project must always keep at least one project manager.
     *
     * <p>No constraint can express this: it is a count over the rows that would remain after the
     * change, which SQL cannot check per-statement. Without it a manager can remove themselves and
     * leave a project nobody can administer — the members are all still there, working, and not one
     * of them can add the next person or approve a request. Only an administrator could unstick it.
     */
    private void requireNotTheLastProjectManager(ProjectMember member, String action) {
        if (member.getRole() != ProjectRole.PROJECT_MANAGER) {
            return;
        }
        long managers = memberRepository.countByProjectIdAndRole(
                member.getProject().getId(), ProjectRole.PROJECT_MANAGER);
        if (managers <= 1) {
            throw new BusinessRuleException("LAST_PROJECT_MANAGER",
                    "You cannot " + action + " the only project manager. Promote somebody else first.");
        }
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
    }

    private ProjectMember requireMembership(Long projectId, Long userId) {
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User " + userId + " is not a member of project " + projectId));
    }
}
