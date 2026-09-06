package ma.dev.workflow.auth.admin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.account.repositories.AccountRepository;
import ma.dev.workflow.auth.admin.dto.AdminAccountDTO;
import ma.dev.workflow.auth.admin.service.IAdminAccountService;
import ma.dev.workflow.auth.client.WorkServiceClient;
import ma.dev.workflow.auth.common.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The administrator's half of the system: who exists, what they are, and whether they may log in.
 *
 * <p>Note what is <em>not</em> here — putting people on projects. That is the project manager's,
 * in work-service, and deliberately so: an administrator who had to assign every person to every
 * project would become the bottleneck the project role exists to remove. This service decides who
 * exists on the platform at all; the chef de projet decides who is on his project.
 */
@Service
@Transactional(readOnly = true)
public class AdminAccountService implements IAdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    private final AccountRepository accountRepository;
    private final WorkServiceClient workServiceClient;

    public AdminAccountService(AccountRepository accountRepository,
                               WorkServiceClient workServiceClient) {
        this.accountRepository = accountRepository;
        this.workServiceClient = workServiceClient;
    }

    /**
     * Assembled from two services, in one extra call rather than one per row.
     *
     * <p>{@code account} has no email — that is a deliberate decision recorded in
     * docs/BACKLOG.md item 17, so there is never a second copy of an address with no owner. The
     * cost lands exactly here: this screen wants an email next to each username, so the profiles
     * are fetched once and joined in memory by {@code work_user_id}.
     */
    @Override
    public List<AdminAccountDTO> findAll(AccountStatus status) {
        List<Account> accounts = status == null
                ? accountRepository.findAllByOrderByCreatedAtDesc()
                : accountRepository.findByStatusOrderByCreatedAtAsc(status);

        Map<Long, WorkServiceClient.WorkUser> profiles = workServiceClient.listUsers().stream()
                .collect(Collectors.toMap(WorkServiceClient.WorkUser::id, Function.identity(),
                        // Ids are unique in work-service; this only exists so a surprise duplicate
                        // is a kept row rather than an exception on an administrator's screen.
                        (first, second) -> first));

        return accounts.stream().map(account -> toDto(account, profiles)).toList();
    }

    @Override
    @Transactional
    public AdminAccountDTO approve(Long id, Role role) {
        Account account = getOrThrow(id);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("NOT_PENDING",
                    "This account is already " + account.getStatus().name().toLowerCase() + ".");
        }

        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        // The profile is told after the decision is committed here. This service owns the answer
        // to "may you log in"; work-service keeps a copy so it can grey out an assignee without a
        // network call. If the mirror fails the person can still sign in - see mirror().
        mirror(saved, role, true);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AdminAccountDTO reject(Long id) {
        Account account = getOrThrow(id);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("NOT_PENDING",
                    "This account is already " + account.getStatus().name().toLowerCase() + ".");
        }
        // DISABLED, not deleted. issue.reporter_id is ON DELETE RESTRICT for a reason, and the
        // username stays taken - which is correct, because somebody did register it.
        return setStatus(id, AccountStatus.DISABLED);
    }

    @Override
    @Transactional
    public AdminAccountDTO changeRole(Long id, Role role) {
        Account account = getOrThrow(id);
        account.setRole(role);
        Account saved = accountRepository.save(account);
        mirror(saved, role, saved.getStatus() == AccountStatus.ACTIVE);

        // Worth knowing, and worth saying at a defence: the person keeps their old role until
        // their current token expires, because a JWT cannot be recalled. An hour, by configuration.
        // A revocation list is the real answer and it is a subsystem - docs/BACKLOG.md item 14.
        log.info("Role of '{}' changed to {}. Their existing token keeps the old role until it expires.",
                saved.getUsername(), role);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AdminAccountDTO setStatus(Long id, AccountStatus status) {
        Account account = getOrThrow(id);
        account.setStatus(status);
        Account saved = accountRepository.save(account);
        mirror(saved, saved.getRole(), status == AccountStatus.ACTIVE);
        return toDto(saved);
    }

    /**
     * Copies the decision into work-service's profile.
     *
     * <p>A second cross-service write, and it can fail on its own — the same shape of problem as
     * registration, recorded as docs/BACKLOG.md item 13. It is treated the same way: logged loudly
     * with the id, not swallowed and not allowed to undo the decision. The failure is survivable
     * in a specific way worth stating — the authoritative column is here, so the person can still
     * log in; what goes stale is a dropdown in work-service, which the next successful change fixes.
     */
    private void mirror(Account account, Role role, boolean active) {
        try {
            workServiceClient.updateRole(account.getWorkUserId(), role);
            workServiceClient.updateActive(account.getWorkUserId(), active);
        } catch (RuntimeException ex) {
            log.error("Account {} ('{}') was updated here but work-service profile {} was not. "
                            + "The login is correct; the profile's role or active flag is stale.",
                    account.getId(), account.getUsername(), account.getWorkUserId(), ex);
        }
    }

    private Account getOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + id));
    }

    private AdminAccountDTO toDto(Account account) {
        return toDto(account, Map.of());
    }

    private AdminAccountDTO toDto(Account account, Map<Long, WorkServiceClient.WorkUser> profiles) {
        WorkServiceClient.WorkUser profile = profiles.get(account.getWorkUserId());
        return new AdminAccountDTO(
                account.getId(),
                account.getWorkUserId(),
                account.getUsername(),
                profile == null ? null : profile.email(),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
