package ma.dev.workflow.common.security;

import ma.dev.workflow.common.exception.ForbiddenException;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.user.models.enums.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Every "may this person touch this project" question in the system, answered in one place.
 *
 * <p><strong>Why not {@code @PreAuthorize}.</strong> Method security decides from what is on the
 * method signature. Half these checks cannot: {@code GET /issues/{id}} has to load the issue before
 * anyone knows which project it belongs to, and therefore whether the caller is allowed to have
 * seen it at all. So the checks live in the service layer, after the row is in hand — the same
 * place, and for the same reason, as the status transition map.
 *
 * <p><strong>Two levels of role, deliberately.</strong> The global role ({@link Role}, carried in
 * the token) says what somebody is on the platform. The project role ({@link ProjectRole}, read
 * from the database) says what they are inside one project. Neither can express the other: a global
 * role cannot say "manager here, ordinary member there", and a project role cannot exist before any
 * project does, which is the situation the first administrator is in.
 *
 * <p><strong>ADMIN passes everything.</strong> Not by being given a membership row in every
 * project — that would be a lie that needs maintaining, and it would break the moment a project was
 * created while nobody was looking. It is one short-circuit, stated once, here.
 */
@Component
public class ProjectAccess {

    private final CurrentUser currentUser;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccess(CurrentUser currentUser, ProjectMemberRepository memberRepository) {
        this.currentUser = currentUser;
        this.memberRepository = memberRepository;
    }

    public Long currentUserId() {
        return currentUser.requireId();
    }

    public boolean isAdmin() {
        return currentUser.isAdmin();
    }

    /**
     * The projects whose contents the caller may read.
     *
     * <p>Callers must check {@link #isAdmin()} first: an administrator sees everything, and this
     * list would say they see nothing, because an administrator is normally a member of no project.
     * Failing open would be the worse mistake, so this method never encodes "all".
     */
    public List<Long> myProjectIds() {
        return memberRepository.findProjectIdsByUserId(currentUserId());
    }

    /** The caller's role on this project, or empty when they are not on it. */
    public Optional<ProjectRole> roleOn(Long projectId) {
        return memberRepository.findByProjectIdAndUserId(projectId, currentUserId())
                .map(ProjectMember::getRole);
    }

    /**
     * The caller must be on this project.
     *
     * <p>403 and not 404. Hiding the project's existence behind "not found" is a defensible choice,
     * but it makes every genuine typo look like a permission problem and vice versa, and this system
     * already leaks project keys through issue keys anyway. An honest error is worth more here than
     * a weak secret.
     */
    public void requireMember(Long projectId) {
        if (isAdmin()) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUserId(projectId, currentUserId())) {
            throw new ForbiddenException("NOT_A_MEMBER", "You are not a member of this project.");
        }
    }

    /** The caller must be the chef de projet — able to change this project's people and settings. */
    public void requireProjectManager(Long projectId) {
        if (isAdmin()) {
            return;
        }
        boolean manages = roleOn(projectId).filter(ProjectRole.PROJECT_MANAGER::equals).isPresent();
        if (!manages) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER",
                    "Only a project manager can do this.");
        }
    }

    /**
     * Creating a project needs the global MANAGER or ADMIN role.
     *
     * <p>This is the one thing MANAGER means, and until now it meant nothing: the role was chosen
     * at registration and never read again. A DEVELOPER joins projects; they do not start them.
     */
    public void requireCanCreateProjects() {
        Role role = currentUser.role().orElseThrow(() -> new ForbiddenException("NOT_A_USER",
                "This action must be performed by a signed-in user, not by a service."));
        if (role != Role.MANAGER && role != Role.ADMIN) {
            throw new ForbiddenException("CANNOT_CREATE_PROJECTS",
                    "Only a manager or an administrator can create a project.");
        }
    }
}
