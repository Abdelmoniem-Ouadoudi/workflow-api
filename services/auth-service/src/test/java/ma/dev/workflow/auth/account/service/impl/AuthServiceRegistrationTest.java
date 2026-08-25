package ma.dev.workflow.auth.account.service.impl;

import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.TokenResponse;
import ma.dev.workflow.auth.account.models.Account;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(jwtIssuer.issueForUser(any())).thenReturn(
                new TokenResponse("a.token.value", Instant.now(), 7L, "moni", Role.ADMIN));
    }

    @Test
    @DisplayName("the password is hashed, and the plain text is never stored")
    void hashesThePassword() {
        when(workServiceClient.createUser(anyString(), anyString(), any())).thenReturn(7L);

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
        when(workServiceClient.createUser("moni", "moni@dev.ma", Role.ADMIN)).thenReturn(7L);

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

        verify(workServiceClient, never()).createUser(anyString(), anyString(), any());
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
        when(workServiceClient.createUser(anyString(), anyString(), any()))
                .thenThrow(new ExternalServiceException("work-service is down"));

        assertThatThrownBy(() -> service.register(request("moni", "password123")))
                .isInstanceOf(ExternalServiceException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("registering signs you in, so nobody types their password twice in a row")
    void returnsATokenStraightAway() {
        when(workServiceClient.createUser(anyString(), anyString(), any())).thenReturn(7L);

        TokenResponse response = service.register(request("moni", "password123"));

        assertThat(response.token()).isEqualTo("a.token.value");
        assertThat(response.userId()).isEqualTo(7L);
    }

    /**
     * auth-service stores a login, not a person. Keeping the email as well would be a second copy
     * with no owner for the disagreement when somebody changes their address.
     */
    @Test
    @DisplayName("the email is forwarded and forgotten, never stored here")
    void doesNotKeepTheEmail() {
        when(workServiceClient.createUser(anyString(), anyString(), any())).thenReturn(7L);

        service.register(request("moni", "password123"));

        verify(workServiceClient).createUser("moni", "moni@dev.ma", Role.ADMIN);
        // Account has no email field at all - this asserts the shape stays that way.
        assertThat(Account.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("email"));
    }

    @Test
    @DisplayName("a new account starts active")
    void startsActive() {
        when(workServiceClient.createUser(anyString(), anyString(), any())).thenReturn(7L);

        service.register(request("moni", "password123"));

        assertThat(savedAccount().getActive()).isTrue();
    }

    private Account savedAccount() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    private RegisterRequest request(String username, String password) {
        return new RegisterRequest(username, username + "@dev.ma", password, Role.ADMIN);
    }
}
