package ma.dev.workflow.project.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.dto.mapper.JoinRequestMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.models.ProjectJoinRequest;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.JoinRequestStatus;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectJoinRequestRepository;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Joining a project with a code somebody mailed you.
 *
 * <p>The design decision under test is that the code does not admit anybody — it only earns the
 * right to ask. A mailed code gets forwarded, quoted in a reply-all and pasted into a chat, so
 * treating possession of one as permission would make every project as private as its most
 * careless member's mailbox.
 */
class ProjectJoinRequestServiceTest {

    private static final Long PROJECT = 1L;
    private static final Long OTHER_PROJECT = 2L;
    private static final Long ME = 7L;
    private static final String CODE = "AAAABBBBCCCC";

    private ProjectRepository projectRepository;
    private ProjectJoinRequestRepository requestRepository;
    private ProjectMemberRepository memberRepository;
    private ProjectAccess projectAccess;
    private ProjectJoinRequestService service;

    private Project project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        requestRepository = mock(ProjectJoinRequestRepository.class);
        memberRepository = mock(ProjectMemberRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JoinRequestMapper mapper = mock(JoinRequestMapper.class);
        projectAccess = mock(ProjectAccess.class);

        service = new ProjectJoinRequestService(projectRepository, requestRepository,
                memberRepository, userRepository, mapper, projectAccess);

        project = new Project();
        project.setId(PROJECT);
        project.setKey("TEST");
        project.setName("Test project");
        project.setJoinCode(CODE);

        User me = new User();
        me.setId(ME);

        when(projectAccess.currentUserId()).thenReturn(ME);
        when(projectRepository.findByJoinCode(CODE)).thenReturn(Optional.of(project));
        when(projectRepository.findByJoinCode("WRONGCODE123")).thenReturn(Optional.empty());
        when(userRepository.findById(ME)).thenReturn(Optional.of(me));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a code nobody uses finds nothing")
    void refusesAnUnknownCode() {
        assertThatThrownBy(() -> service.lookupByJoinCode("WRONGCODE123"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** Enough to check you have the right project, and nothing more. */
    @Test
    @DisplayName("a valid code reveals the project's name and key, and no more")
    void looksUpTheProject() {
        var found = service.lookupByJoinCode(CODE);

        assertThat(found.id()).isEqualTo(PROJECT);
        assertThat(found.key()).isEqualTo("TEST");
        assertThat(found.name()).isEqualTo("Test project");
    }

    /**
     * Without this, a valid code for one project would let somebody ask to join another — the code
     * proves you were told about <em>that</em> project, so it must be checked against that one.
     */
    @Test
    @DisplayName("a code for one project cannot be used to ask about another")
    void refusesACodeFromAnotherProject() {
        assertThatThrownBy(() -> service.request(OTHER_PROJECT, CODE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("JOIN_CODE_MISMATCH");
    }

    @Test
    @DisplayName("somebody already on the project does not ask to join it")
    void refusesWhenAlreadyAMember() {
        when(memberRepository.existsByProjectIdAndUserId(PROJECT, ME)).thenReturn(true);

        assertThatThrownBy(() -> service.request(PROJECT, CODE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("ALREADY_A_MEMBER");
    }

    @Test
    @DisplayName("asking twice while the first request is open is refused")
    void refusesASecondPendingRequest() {
        when(requestRepository.findByProjectIdAndUserIdAndStatus(PROJECT, ME, JoinRequestStatus.PENDING))
                .thenReturn(Optional.of(new ProjectJoinRequest()));

        assertThatThrownBy(() -> service.request(PROJECT, CODE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("REQUEST_ALREADY_PENDING");
    }

    /**
     * The important half: a valid code produces a <em>request</em>, not a membership. Nobody is on
     * the project until a project manager says so.
     */
    @Test
    @DisplayName("a valid code creates a pending request and no membership")
    void createsAPendingRequest() {
        service.request(PROJECT, CODE);

        ArgumentCaptor<ProjectJoinRequest> captor = ArgumentCaptor.forClass(ProjectJoinRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("approving creates exactly one membership, as an ordinary member")
    void approvingCreatesTheMembership() {
        givenPendingRequest(99L);

        service.approve(PROJECT, 99L);

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(memberRepository).save(captor.capture());
        // MEMBER, never PROJECT_MANAGER: joining a project does not make you its chef.
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    @DisplayName("approving twice is refused, so nobody is added to a project twice")
    void refusesApprovingAnAlreadyDecidedRequest() {
        ProjectJoinRequest decided = givenPendingRequest(99L);
        decided.setStatus(JoinRequestStatus.APPROVED);

        assertThatThrownBy(() -> service.approve(PROJECT, 99L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("REQUEST_ALREADY_DECIDED");

        verify(memberRepository, never()).save(any());
    }

    /** Otherwise a manager of one project could answer requests belonging to another. */
    @Test
    @DisplayName("a request belonging to another project is not found here")
    void refusesARequestFromAnotherProject() {
        givenPendingRequest(99L);

        assertThatThrownBy(() -> service.approve(OTHER_PROJECT, 99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** Rejected rows are kept: who asked and who said no is the record. */
    @Test
    @DisplayName("rejecting records the decision and never deletes the row")
    void rejectingKeepsTheRow() {
        givenPendingRequest(99L);

        service.reject(PROJECT, 99L);

        ArgumentCaptor<ProjectJoinRequest> captor = ArgumentCaptor.forClass(ProjectJoinRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(captor.getValue().getDecidedAt()).isNotNull();
        verify(requestRepository, never()).delete(any());
        verify(memberRepository, never()).save(any());
    }

    private ProjectJoinRequest givenPendingRequest(Long requestId) {
        User asker = new User();
        asker.setId(8L);

        ProjectJoinRequest request = new ProjectJoinRequest();
        request.setId(requestId);
        request.setProject(project);
        request.setUser(asker);
        request.setStatus(JoinRequestStatus.PENDING);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        return request;
    }
}
