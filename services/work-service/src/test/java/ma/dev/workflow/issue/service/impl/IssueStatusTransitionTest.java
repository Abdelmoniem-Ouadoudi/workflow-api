package ma.dev.workflow.issue.service.impl;

import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.CurrentUser;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.issue.dto.IssueDTO;
import ma.dev.workflow.issue.dto.IssueStatusUpdateDTO;
import ma.dev.workflow.issue.dto.mapper.IssueMapper;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.Status;
import ma.dev.workflow.issue.repositories.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The workflow rules.
 *
 * <p>These are the rules the database cannot hold: `status` is a varchar, so nothing in the schema
 * stops an issue jumping from TO_DO to DONE. The only thing that does is
 * {@code IssueService.ALLOWED_TRANSITIONS}, which makes it exactly the kind of rule worth a test —
 * it is invisible in the data and one map entry away from being wrong.
 *
 * <p>Plain Mockito, no Spring context. These are decisions in Java, so a test that needed a
 * database and a broker to check them would be testing the wrong thing and would be too slow to
 * run often.
 */
class IssueStatusTransitionTest {

    private IssueRepository issueRepository;
    private IssueMapper issueMapper;
    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        issueMapper = mock(IssueMapper.class);

        // updateStatus touches only these two. The rest are mocked because the constructor asks
        // for them, which is itself a signal: this service does enough that a change to how boards
        // are looked up can break a test about status.
        issueService = new IssueService(
                issueRepository,
                mock(ma.dev.workflow.project.repositories.ProjectRepository.class),
                mock(ma.dev.workflow.board.repositories.BoardRepository.class),
                mock(ma.dev.workflow.sprint.repositories.SprintRepository.class),
                mock(ma.dev.workflow.user.repositories.UserRepository.class),
                mock(ma.dev.workflow.project.repositories.ProjectMemberRepository.class),
                issueMapper,
                mock(CurrentUser.class),
                // A mock that refuses nothing: this test is about the transition map, not about
                // who is allowed to move a card. ProjectAccessTest covers that half.
                mock(ProjectAccess.class),
                mock(ApplicationEventPublisher.class));

        when(issueMapper.fromModel(any())).thenReturn(new IssueDTO());
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "TO_DO,       IN_PROGRESS",
            "IN_PROGRESS, DONE",
            "IN_PROGRESS, TO_DO",
            "DONE,        IN_PROGRESS"
    })
    void allowsTheTransitionsTheWorkflowDeclares(Status from, Status to) {
        Issue issue = issueAt(from);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        issueService.updateStatus(1L, statusUpdate(to, null));

        assertThat(issue.getStatus()).isEqualTo(to);
    }

    /**
     * The rule the board exists to enforce: work cannot be declared finished without having been
     * started, and finished work cannot be reopened straight back to the backlog.
     */
    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            "TO_DO, DONE",
            "DONE,  TO_DO"
    })
    void refusesTheTransitionsTheWorkflowForbids(Status from, Status to) {
        Issue issue = issueAt(from);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> issueService.updateStatus(1L, statusUpdate(to, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot move an issue from " + from + " to " + to)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("ILLEGAL_TRANSITION");

        assertThat(issue.getStatus()).isEqualTo(from);
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("dropping a card back where it came from is a no-op, not an error")
    void treatsAMoveToTheSameStatusAsNothingToDo() {
        Issue issue = issueAt(Status.IN_PROGRESS);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));

        issueService.updateStatus(1L, statusUpdate(Status.IN_PROGRESS, null));

        // No write, so no version bump. Otherwise picking a card up and putting it back would
        // silently invalidate the version every other tab is holding.
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a stale version is refused instead of overwriting someone else's move")
    void refusesAStaleVersion() {
        Issue issue = issueAt(Status.TO_DO);
        issue.setVersion(4L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> issueService.updateStatus(1L, statusUpdate(Status.IN_PROGRESS, 3L)))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("was changed by someone else");

        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a client that sends no version is trusted, because it never had one")
    void allowsAMoveWithNoVersion() {
        Issue issue = issueAt(Status.TO_DO);
        issue.setVersion(4L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        issueService.updateStatus(1L, statusUpdate(Status.IN_PROGRESS, null));

        assertThat(issue.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    private Issue issueAt(Status status) {
        // The project is here only because updateStatus now reads it to check membership.
        Project project = new Project();
        project.setId(1L);
        project.setKey("TEST");

        Issue issue = new Issue();
        issue.setId(1L);
        issue.setProject(project);
        issue.setIssueKey("TEST-1");
        issue.setStatus(status);
        return issue;
    }

    private IssueStatusUpdateDTO statusUpdate(Status status, Long version) {
        IssueStatusUpdateDTO dto = new IssueStatusUpdateDTO();
        dto.setStatus(status);
        dto.setVersion(version);
        return dto;
    }
}
