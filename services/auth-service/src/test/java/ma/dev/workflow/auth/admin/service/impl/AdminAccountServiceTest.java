package ma.dev.workflow.auth.admin.service.impl;

import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.account.repositories.AccountRepository;
import ma.dev.workflow.auth.admin.dto.AdminAccountDTO;
import ma.dev.workflow.auth.client.WorkServiceClient;
import ma.dev.workflow.auth.common.exception.BusinessRuleException;
import ma.dev.workflow.auth.common.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approving somebody, and the two things that go with it.
 *
 * <p>Approval writes in two databases: the status here, which decides whether a login works, and a
 * copy in work-service, which decides whether somebody appears in an assignee dropdown. Which of
 * those two is allowed to fail, and what happens when it does, is a decision rather than an
 * accident — so it is pinned here.
 */
class AdminAccountServiceTest {

    private static final Long ACCOUNT_ID = 5L;
    private static final Long WORK_USER_ID = 7L;

    private AccountRepository accountRepository;
    private WorkServiceClient workServiceClient;
    private AdminAccountService service;

    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        workServiceClient = mock(WorkServiceClient.class);
        service = new AdminAccountService(accountRepository, workServiceClient);

        account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUsername("moni");
        account.setRole(Role.DEVELOPER);
        account.setStatus(AccountStatus.PENDING);
        account.setWorkUserId(WORK_USER_ID);

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("approving activates the account and sets the role the administrator chose")
    void approveActivatesAndSetsTheRole() {
        AdminAccountDTO approved = service.approve(ACCOUNT_ID, Role.MANAGER);

        assertThat(approved.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(approved.role()).isEqualTo(Role.MANAGER);
    }

    /**
     * The role is mirrored into work-service, which is the fix for a real bug: before M5 the two
     * copies were written by different endpoints and nothing kept them in step, so a promoted
     * person kept their old role in every token that was ever issued to them.
     */
    @Test
    @DisplayName("approving mirrors the role and the active flag into the profile")
    void approveMirrorsToWorkService() {
        service.approve(ACCOUNT_ID, Role.MANAGER);

        verify(workServiceClient).updateRole(WORK_USER_ID, Role.MANAGER);
        verify(workServiceClient).updateActive(WORK_USER_ID, true);
    }

    @Test
    @DisplayName("an account that was already decided is not approved a second time")
    void refusesApprovingTwice() {
        account.setStatus(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.approve(ACCOUNT_ID, Role.MANAGER))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("NOT_PENDING");
    }

    /**
     * Rejecting is a status change, never a delete. {@code issue.reporter_id} is ON DELETE RESTRICT
     * on purpose, and the username stays taken — somebody did register it.
     */
    @Test
    @DisplayName("rejecting disables the account and never deletes it")
    void rejectDisablesRatherThanDeletes() {
        AdminAccountDTO rejected = service.reject(ACCOUNT_ID);

        assertThat(rejected.status()).isEqualTo(AccountStatus.DISABLED);
        verify(accountRepository, never()).delete(any());
        verify(accountRepository, never()).deleteById(anyLong());
    }

    /**
     * The mirror is allowed to fail; the decision is not undone.
     *
     * <p>This is the dual-write problem again — BACKLOG item 13 — and the answer is the same one
     * registration gives: fail loudly in the log rather than roll back. The reason it is survivable
     * here is specific. The authoritative column is the one in this service, so a person whose
     * mirror failed can still log in with the right role; what goes stale is a dropdown in
     * work-service, and the next successful change repairs it. Throwing instead would leave the
     * administrator staring at an error for a decision that was in fact recorded.
     */
    @Test
    @DisplayName("if the profile cannot be told, the approval still stands")
    void approvalSurvivesAFailedMirror() {
        doThrow(new ExternalServiceException("work-service is down"))
                .when(workServiceClient).updateRole(anyLong(), any());

        assertThatCode(() -> service.approve(ACCOUNT_ID, Role.MANAGER)).doesNotThrowAnyException();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("disabling an account switches it off without touching its role")
    void disableKeepsTheRole() {
        account.setStatus(AccountStatus.ACTIVE);
        account.setRole(Role.MANAGER);

        AdminAccountDTO disabled = service.setStatus(ACCOUNT_ID, AccountStatus.DISABLED);

        assertThat(disabled.status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(disabled.role()).isEqualTo(Role.MANAGER);
        verify(workServiceClient).updateActive(WORK_USER_ID, false);
    }

    /**
     * The email is not in this database by design, so the list has to be joined across two
     * services — in one call, not one per row.
     */
    @Test
    @DisplayName("the list is joined with the profiles to get an email")
    void listJoinsTheProfileForAnEmail() {
        when(accountRepository.findByStatusOrderByCreatedAtAsc(AccountStatus.PENDING))
                .thenReturn(List.of(account));
        when(workServiceClient.listUsers()).thenReturn(
                List.of(new WorkServiceClient.WorkUser(WORK_USER_ID, "moni", "moni@dev.ma", false)));

        List<AdminAccountDTO> pending = service.findAll(AccountStatus.PENDING);

        assertThat(pending).singleElement()
                .satisfies(row -> {
                    assertThat(row.username()).isEqualTo("moni");
                    assertThat(row.email()).isEqualTo("moni@dev.ma");
                });
    }

    /**
     * A half-failed registration leaves an account whose profile was never created. Hiding that row
     * would hide the one person an administrator most needs to see.
     */
    @Test
    @DisplayName("an account with no matching profile is still listed, with no email")
    void listKeepsAccountsWithNoProfile() {
        when(accountRepository.findByStatusOrderByCreatedAtAsc(AccountStatus.PENDING))
                .thenReturn(List.of(account));
        when(workServiceClient.listUsers()).thenReturn(List.of());

        List<AdminAccountDTO> pending = service.findAll(AccountStatus.PENDING);

        assertThat(pending).singleElement()
                .satisfies(row -> {
                    assertThat(row.username()).isEqualTo("moni");
                    assertThat(row.email()).isNull();
                });
    }

    @Test
    @DisplayName("changing a role after approval mirrors it too")
    void changeRoleMirrors() {
        account.setStatus(AccountStatus.ACTIVE);

        service.changeRole(ACCOUNT_ID, Role.ADMIN);

        assertThat(account.getRole()).isEqualTo(Role.ADMIN);
        verify(workServiceClient).updateRole(WORK_USER_ID, Role.ADMIN);
        verify(workServiceClient).updateActive(WORK_USER_ID, true);
    }

    /** A disabled account that gets a new role must not be switched back on as a side effect. */
    @Test
    @DisplayName("changing the role of a disabled account leaves it disabled")
    void changeRoleDoesNotReactivate() {
        account.setStatus(AccountStatus.DISABLED);

        service.changeRole(ACCOUNT_ID, Role.ADMIN);

        verify(workServiceClient).updateActive(WORK_USER_ID, false);
    }

    @Test
    @DisplayName("only a pending account can be rejected")
    void refusesRejectingADecidedAccount() {
        account.setStatus(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.reject(ACCOUNT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("NOT_PENDING");

        verify(workServiceClient, never()).updateActive(anyLong(), anyBoolean());
    }
}
