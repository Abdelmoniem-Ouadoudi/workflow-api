package ma.dev.workflow.auth.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ma.dev.workflow.auth.account.dto.TokenResponse;
import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is actually inside a token.
 *
 * <p>Signed with a real encoder and read back with a real decoder rather than mocked. Mocking the
 * encoder would test that a method was called; this tests that the token another service will
 * receive can be verified and says what it is supposed to say — which is the only thing that
 * matters, because three other services depend on these exact claim names.
 */
class JwtIssuerTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hs256";
    private static final String ISSUER = "auth-service";

    private JwtIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        issuer = new JwtIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                ISSUER, Duration.ofHours(1));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * The claim that makes the system work without chatter: an issue's reporter is a work-service
     * id, so carrying it means nothing ever has to call back here to ask who the caller is.
     */
    @Test
    @DisplayName("a user token carries the work-service id, not the account id")
    void carriesTheWorkServiceUserId() {
        TokenResponse response = issuer.issueForUser(account(42L, "moni", Role.ADMIN));

        Jwt decoded = decoder.decode(response.token());

        assertThat(decoded.getClaim(JwtIssuer.CLAIM_USER_ID).toString()).isEqualTo("42");
        assertThat(decoded.getSubject()).isEqualTo("moni");
        assertThat(decoded.getClaimAsString(JwtIssuer.CLAIM_ROLE)).isEqualTo("ADMIN");
        // Read as a string, not through getIssuer(): that accessor insists on a URL, and this
        // issuer is the plain name "auth-service". Which is fine - the resource servers compare
        // it as a string too - but it is worth knowing before somebody "fixes" it to a URL.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(ISSUER);
    }

    @Test
    @DisplayName("the response repeats the work-service id, so the browser never parses the token")
    void reportsTheIdsAlongsideTheToken() {
        TokenResponse response = issuer.issueForUser(account(42L, "moni", Role.DEVELOPER));

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.username()).isEqualTo("moni");
        assertThat(response.role()).isEqualTo(Role.DEVELOPER);
    }

    @Test
    @DisplayName("a user token lasts the configured hour")
    void expiresAfterTheConfiguredLifetime() {
        TokenResponse response = issuer.issueForUser(account(1L, "moni", Role.DEVELOPER));

        assertThat(response.expiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofHours(1)),
                        org.assertj.core.api.Assertions.within(Duration.ofSeconds(30)));
    }

    /**
     * Registration happens before the person has a token, so that call needs its own identity.
     * Reusing the same JWT mechanism with a distinct role is what lets work-service require
     * ROLE_SERVICE on POST /users instead of leaving it open.
     */
    @Test
    @DisplayName("a service token says SERVICE and carries no user id at all")
    void issuesAServiceTokenWithNoUser() {
        Jwt decoded = decoder.decode(issuer.issueForService());

        assertThat(decoded.getClaimAsString(JwtIssuer.CLAIM_ROLE)).isEqualTo(JwtIssuer.SERVICE_ROLE);
        assertThat(decoded.getSubject()).isEqualTo(ISSUER);
        // No uid, which is what work-service's CurrentUser refuses to write as a reporter.
        // Cast because Jwt.getClaim is generic: without it, AssertJ cannot tell which overload
        // is meant and the inference is ambiguous rather than wrong.
        assertThat((Object) decoded.getClaim(JwtIssuer.CLAIM_USER_ID)).isNull();
    }

    /**
     * A service token exists only for the length of one downstream call. Short means a leaked one
     * is useless within the minute.
     */
    @Test
    @DisplayName("a service token is short-lived, far shorter than a person's")
    void makesServiceTokensShortLived() {
        Jwt decoded = decoder.decode(issuer.issueForService());

        assertThat(decoded.getExpiresAt())
                .isBefore(Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("a token signed with a different secret is refused")
    void refusesATokenSignedWithAnotherKey() {
        SecretKey otherKey = new SecretKeySpec(
                "a-completely-different-secret-of-the-right-length".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtIssuer impostor = new JwtIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)),
                ISSUER, Duration.ofHours(1));

        String forged = impostor.issueForUser(account(1L, "attacker", Role.ADMIN)).token();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(forged))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    private Account account(Long workUserId, String username, Role role) {
        Account account = new Account();
        account.setId(1L);
        account.setUsername(username);
        account.setWorkUserId(workUserId);
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
