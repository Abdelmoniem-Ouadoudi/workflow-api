package ma.dev.workflow.project.service.impl;

import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.dto.AddMemberRequest;
import ma.dev.workflow.project.dto.mapper.ProjectMemberMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules about a project's people that the schema cannot hold.
 *
 * <p>The one that matters is the last project manager. It is a count over the rows that would
 * remain <em>after</em> a change, which no constraint can express per-statement — and its failure
 * mode is quiet in the worst way: everybody is still there and still working, and not one of them
 * can add the next person or answer a join request. Nothing errors. The project simply becomes
 * unadministrable until somebody notices and finds an administrator.
 */
class ProjectMemberServiceTest {

    private static final Long PROJECT = 1L;
    private static final Long THE_CHEF = 7L;
    private static final Long SOMEBODY_ELSE = 8L;

    private ProjectRepository projectRepository;
    private ProjectMemberRepository memberRepository;
    private UserRepository userRepository;
    private ProjectMemberService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        memberRepository = mock(ProjectMemberRepository.class);
        userRepository = mock(UserRepository.class);
        ProjectMemberMapper mapper = mock(ProjectMemberMapper.class);

        service = new ProjectMemberService(projectRepository, memberRepository, userRepository,
                mapper, mock(ProjectAccess.class), new JoinCodeGenerator());

        Project project = new Project();
        project.setId(PROJECT);
        project.setKey("TEST");
        project.setJoinCode("AAAABBBBCCCC");
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("the only project manager cannot be removed")
    void refusesRemovingTheLastProjectManager() {
        givenMembership(THE_CHEF, ProjectRole.PROJECT_MANAGER);
        when(memberRepository.countByProjectIdAndRole(PROJECT, ProjectRole.PROJECT_MANAGER))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.remove(PROJECT, THE_CHEF))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("LAST_PROJECT_MANAGER");

        verify(memberRepository, never()).delete(any());
    }

    /** Demoting yourself is the same act as removing yourself, as far as the project is concerned. */
    @Test
    @DisplayName("the only project manager cannot demote themselves either")
    void refusesDemotingTheLastProjectManager() {
        givenMembership(THE_CHEF, ProjectRole.PROJECT_MANAGER);
        when(memberRepository.countByProjectIdAndRole(PROJECT, ProjectRole.PROJECT_MANAGER))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(PROJECT, THE_CHEF, ProjectRole.MEMBER))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("LAST_PROJECT_MANAGER");
    }

    @Test
    @DisplayName("a project manager can leave once somebody else runs it too")
    void allowsRemovingAManagerWhenAnotherRemains() {
        givenMembership(THE_CHEF, ProjectRole.PROJECT_MANAGER);
        when(memberRepository.countByProjectIdAndRole(PROJECT, ProjectRole.PROJECT_MANAGER))
                .thenReturn(2L);

        assertThatCode(() -> service.remove(PROJECT, THE_CHEF)).doesNotThrowAnyException();
        verify(memberRepository).delete(any());
    }

    /** The rule is about managers, not about people. An ordinary member always leaves cleanly. */
    @Test
    @DisplayName("an ordinary member can always be removed")
    void allowsRemovingAnOrdinaryMember() {
        givenMembership(SOMEBODY_ELSE, ProjectRole.MEMBER);

        assertThatCode(() -> service.remove(PROJECT, SOMEBODY_ELSE)).doesNotThrowAnyException();
        verify(memberRepository).delete(any());
        // The count is never even asked for: a member leaving cannot be the last manager leaving.
        verify(memberRepository, never()).countByProjectIdAndRole(any(), any());
    }

    /**
     * A project manager could otherwise hand work to somebody an administrator has switched off,
     * or to somebody nobody has approved yet.
     */
    @Test
    @DisplayName("a deactivated or unapproved person cannot be added to a project")
    void refusesAnInactiveUser() {
        User inactive = new User();
        inactive.setId(SOMEBODY_ELSE);
        inactive.setActive(false);
        when(userRepository.findById(SOMEBODY_ELSE)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() ->
                service.add(PROJECT, new AddMemberRequest(SOMEBODY_ELSE, ProjectRole.MEMBER)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("USER_NOT_ACTIVE");
    }

    @Test
    @DisplayName("somebody already on the project is not added twice")
    void refusesADuplicateMember() {
        User active = new User();
        active.setId(SOMEBODY_ELSE);
        active.setActive(true);
        when(userRepository.findById(SOMEBODY_ELSE)).thenReturn(Optional.of(active));
        when(memberRepository.existsByProjectIdAndUserId(PROJECT, SOMEBODY_ELSE)).thenReturn(true);

        assertThatThrownBy(() ->
                service.add(PROJECT, new AddMemberRequest(SOMEBODY_ELSE, ProjectRole.MEMBER)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("ALREADY_A_MEMBER");
    }

    /**
     * Rotating must not lock the current members out. The code only governs who may <em>ask</em>
     * to join next; it is not a password for the project.
     */
    @Test
    @DisplayName("rotating the join code changes only the code")
    void rotatingReplacesOnlyTheCode() {
        String before = service.joinCode(PROJECT);
        String after = service.rotateJoinCode(PROJECT);

        assertThatCode(() -> service.findByProject(PROJECT)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(after).isNotEqualTo(before);
        verify(memberRepository, never()).delete(any());
    }

    private void givenMembership(Long userId, ProjectRole role) {
        Project project = new Project();
        project.setId(PROJECT);

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setRole(role);

        User user = new User();
        user.setId(userId);
        member.setUser(user);

        when(memberRepository.findByProjectIdAndUserId(PROJECT, userId))
                .thenReturn(Optional.of(member));
    }
}
