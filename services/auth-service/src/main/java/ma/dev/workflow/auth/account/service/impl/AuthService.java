package ma.dev.workflow.auth.account.service.impl;

import ma.dev.workflow.auth.account.dto.LoginRequest;
import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.RegistrationReceipt;
import ma.dev.workflow.auth.account.dto.TokenResponse;
import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.account.repositories.AccountRepository;
import ma.dev.workflow.auth.account.service.IAuthService;
import ma.dev.workflow.auth.client.WorkServiceClient;
import ma.dev.workflow.auth.common.exception.BusinessRuleException;
import ma.dev.workflow.auth.token.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final WorkServiceClient workServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtIssuer jwtIssuer;

    public AuthService(AccountRepository accountRepository,
                       WorkServiceClient workServiceClient,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtIssuer jwtIssuer) {
        this.accountRepository = accountRepository;
        this.workServiceClient = workServiceClient;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Two databases, two writes, and only one of them is ours.
     *
     * <p>Deliberately not {@code @Transactional}. A transaction here would hold a pooled database
     * connection open across an HTTP call to another service, and a slow work-service would then
     * drain this service's connection pool. There is one write, and {@code save} is atomic by
     * itself.
     *
     * <p>The honest consequence: if step 3 fails after step 2 succeeded, work-service keeps a
     * profile that has no login. That is the dual-write problem, it has no local fix, and the real
     * answers are an outbox or an idempotent retry. It is written down in docs/BACKLOG.md rather
     * than papered over — the failed call is logged loudly enough to find the orphan.
     */
    @Override
    public RegistrationReceipt register(RegisterRequest request) {
        // 1. Cheap local check first, so a duplicate never causes a pointless call to work-service.
        if (accountRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("USERNAME_TAKEN",
                    "Username already used: " + request.username());
        }

        // 2. The remote write. If this throws, nothing has been created anywhere.
        //
        // Always DEVELOPER, and always inactive. The role is not the registrant's to choose - it
        // used to be, on an open endpoint, which made "register yourself as an administrator" a
        // one-line request. And the profile starts inactive so that somebody nobody has approved
        // cannot be picked out of an assignee dropdown and handed work.
        Long workUserId = workServiceClient.createUser(
                request.username(), request.email(), Role.DEVELOPER, false);

        // 3. The local write. If this throws, the profile from step 2 is orphaned.
        Account account = new Account();
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(Role.DEVELOPER);
        account.setWorkUserId(workUserId);
        account.setStatus(AccountStatus.PENDING);

        try {
            account = accountRepository.save(account);
        } catch (RuntimeException ex) {
            log.error("Registration half-failed: work-service user {} was created for '{}' but the "
                            + "account row could not be saved. That profile now has no login.",
                    workUserId, request.username(), ex);
            throw ex;
        }

        // 4. No token. Registering no longer signs you in, because it no longer lets you in:
        // an administrator has to approve the account first, and handing out a key to a locked
        // door would only move the confusion to the first screen that came back empty.
        return new RegistrationReceipt(account.getUsername(), account.getStatus(),
                "Your account was created and is waiting for an administrator to approve it.");
    }

    /**
     * The password comparison itself is Spring Security's: the manager loads the account through
     * {@link AccountDetailsService}, BCrypt-matches the submitted password against the stored hash,
     * and refuses a deactivated account. Both failures come back as one message, because saying
     * which half was wrong confirms that a username exists.
     */
    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        // Authentication passed, so the row is there. The token needs work_user_id, which is on
        // the entity and not on Spring Security's UserDetails.
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Username or password is wrong."));

        return jwtIssuer.issueForUser(account);
    }
}
