package ma.dev.workflow.auth.account.service.impl;

import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.RegistrationReceipt;
import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.account.repositories.AccountRepository;
import ma.dev.workflow.auth.client.WorkServiceClient;
import ma.dev.workflow.auth.common.exception.BusinessRuleException;
import ma.dev.workflow.auth.common.exception.ExternalServiceException;
import ma.dev.workflow.auth.token.JwtIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration, which writes to two databases and only owns one of them.
 *
 * <p>The order of those two writes is a decision with a consequence, and the consequence is
 * documented in BACKLOG item 13. These tests pin the order down, so a later refactor cannot
 * quietly swap it and turn a recoverable half-failure into an unrecoverable one.
 *
 * <p>Since M5 they also pin down the thing registration is <em>not</em> allowed to do any more:
 * decide what the person is, or let them in.
 */
class AuthServiceRegistrationTest {

    private AccountRepository accountRepository;
    private WorkServiceClient workServiceClient;
    private JwtIssuer jwtIssuer;
    private AuthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        workServiceClient = mock(WorkServiceClient.class);
        jwtIssuer = mock(JwtIssuer.class);

        service = new AuthService(accountRepository, workServiceClient,
                new BCryptPasswordEncoder(), mock(AuthenticationManager.class), jwtIssuer);

        when(accountRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("the password is hashed, and the plain text is never stored")
    void hashesThePassword() {
        givenTheProfileIsCreated();

        service.register(request("moni", "password123"));

        Account saved = savedAccount();
        assertThat(saved.getPasswordHash())
                .isNotEqualTo("password123")
                .startsWith("$2a$");  // BCrypt, with its cost factor inside the hash
        assertThat(new BCryptPasswordEncoder().matches("password123", saved.getPasswordHash()))
                .isTrue();
    }

    /**
     * The id the profile came back with is the id every issue and comment will reference. Getting
     * it wrong would mean an account that authenticates as somebody else.
     */
    @Test
    @DisplayName("the work-service id comes back from the profile call and is stored")
    void storesTheWorkServiceId() {
        when(workServiceClient.createUser("moni", "moni@dev.ma", Role.DEVELOPER, false))
                .thenReturn(7L);

        service.register(request("moni", "password123"));

        assertThat(savedAccount().getWorkUserId()).isEqualTo(7L);
    }

    /**
     * The local check is first so a duplicate never causes a pointless call to another service —
     * and, more importantly, never creates a profile that then cannot be given a login.
     */
    @Test
    @DisplayName("a duplicate username is refused before work-service is troubled")
    void checksLocallyBeforeCallingOut() {
        when(accountRepository.existsByUsername("moni")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("moni", "password123")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("USERNAME_TAKEN");

        verify(workServiceClient, never()).createUser(anyString(), anyString(), any(), anyBoolean());
        verify(accountRepository, never()).save(any());
    }

    /**
     * The remote write comes first, so if it fails nothing has been created anywhere. The reverse
     * order would leave a login with no profile — an account that can sign in and then cannot file
     * anything, which is worse than not registering at all.
     */
    @Test
    @DisplayName("if the profile cannot be created, no account is saved either")
    void savesNothingWhenTheProfileCallFails() {
        when(workServiceClient.createUser(anyString(), anyString(), any(), anyBoolean()))
                .thenThrow(new ExternalServiceException("work-service is down"));

        assertThatThrownBy(() -> service.register(request("moni", "password123")))
                .isInstanceOf(ExternalServiceException.class);

        verify(accountRepository, never()).save(any());
    }

    /**
     * The sharpest rule in M5, and the reason the approval queue exists at all.
     *
     * <p>{@code RegisterRequest} no longer has a role field, so this cannot be sent — but a field
     * removed from a DTO is one pull request away from coming back. What this pins is the service:
     * whatever arrives, the account is a DEVELOPER. Before M5 the caller chose, on a public
     * endpoint, which made "register yourself as an administrator" a single request.
     */
    @Test
    @DisplayName("everybody registers as a DEVELOPER, whatever they ask for")
    void alwaysRegistersAsADeveloper() {
        givenTheProfileIsCreated();

        service.register(request("moni", "password123"));

        assertThat(savedAccount().getRole()).isEqualTo(Role.DEVELOPER);
        // And the profile is told the same thing, so the two copies cannot start out disagreeing.
        verify(workServiceClient).createUser("moni", "moni@dev.ma", Role.DEVELOPER, false);
    }

    @Test
    @DisplayName("a new account is PENDING until an administrator approves it")
    void startsPending() {
        givenTheProfileIsCreated();

        service.register(request("moni", "password123"));

        assertThat(savedAccount().getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    /**
     * Registering used to sign you in, on the reasoning that asking for the password twice in a row
     * would be theatre. That held only while registering was the same act as being allowed in.
     * A token now would be a key to a door that is locked.
     */
    @Test
    @DisplayName("registration returns no token, because it does not let you in")
    void returnsNoToken() {
        givenTheProfileIsCreated();

        RegistrationReceipt receipt = service.register(request("moni", "password123"));

        assertThat(receipt.status()).isEqualTo(AccountStatus.PENDING);
        assertThat(receipt.username()).isEqualTo("moni");
        verify(jwtIssuer, never()).issueForUser(any());
    }

    /**
     * The profile starts inactive so that somebody nobody has approved cannot be picked out of an
     * assignee dropdown and handed work. work-service has no idea what PENDING means — it holds a
     * boolean — so this is where the two vocabularies are translated.
     */
    @Test
    @DisplayName("the profile is created inactive, so an unapproved person cannot be assigned work")
    void createsTheProfileInactive() {
        givenTheProfileIsCreated();

        service.register(request("moni", "password123"));

        verify(workServiceClient).createUser("moni", "moni@dev.ma", Role.DEVELOPER, false);
    }

    /**
     * auth-service stores a login, not a person. Keeping the email as well would be a second copy
     * with no owner for the disagreement when somebody changes their address.
     */
    @Test
    @DisplayName("the email is forwarded and forgotten, never stored here")
    void doesNotKeepTheEmail() {
        givenTheProfileIsCreated();

        service.register(request("moni", "password123"));

        verify(workServiceClient).createUser("moni", "moni@dev.ma", Role.DEVELOPER, false);
        // Account has no email field at all - this asserts the shape stays that way.
        assertThat(Account.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("email"));
    }

    private void givenTheProfileIsCreated() {
        when(workServiceClient.createUser(anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(7L);
    }

    private Account savedAccount() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    private RegisterRequest request(String username, String password) {
        return new RegisterRequest(username, username + "@dev.ma", password);
    }
}
