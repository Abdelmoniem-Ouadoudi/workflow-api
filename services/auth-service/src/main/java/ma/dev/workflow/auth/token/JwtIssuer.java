package ma.dev.workflow.auth.token;

import ma.dev.workflow.auth.account.dto.TokenResponse;
import ma.dev.workflow.auth.account.models.Account;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The only place a token is created. Two kinds come out of it, and they differ only by claims.
 */
@Component
public class JwtIssuer {

    /**
     * Claim holding the work-service user id.
     *
     * <p>This is the claim that makes the system work without chatter: an issue's reporter and a
     * comment's author are work-service ids, so putting it in the token means no service ever has
     * to call back here to ask who the caller is. {@code sub} holds the username, which is for
     * reading logs, not for joining rows.
     */
    public static final String CLAIM_USER_ID = "uid";

    /** Claim holding the role name. work-service turns it into ROLE_&lt;value&gt;. */
    public static final String CLAIM_ROLE = "role";

    /** The role a service gives itself when calling another service. No human ever has it. */
    public static final String SERVICE_ROLE = "SERVICE";

    /**
     * Short on purpose. A service token exists only for the length of one downstream call, so if
     * one ever leaked it would be useless within the minute.
     */
    private static final Duration SERVICE_TOKEN_TTL = Duration.ofSeconds(60);

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration userTokenTtl;

    public JwtIssuer(JwtEncoder jwtEncoder,
                     @Value("${app.jwt.issuer}") String issuer,
                     @Value("${app.jwt.ttl}") Duration userTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.userTokenTtl = userTokenTtl;
    }

    /** The token a person carries. Returned by both register and login. */
    public TokenResponse issueForUser(Account account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(userTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(account.getUsername())
                .claim(CLAIM_USER_ID, account.getWorkUserId())
                .claim(CLAIM_ROLE, account.getRole().name())
                .build();

        return new TokenResponse(sign(claims), expiresAt, account.getWorkUserId(),
                account.getUsername(), account.getRole());
    }

    /**
     * The token this service carries when it calls work-service to create a profile.
     *
     * <p>Registration happens before the person has any token of their own, so that one call needs
     * its own identity. Reusing the same JWT mechanism with a distinct role means work-service can
     * require {@code ROLE_SERVICE} on {@code POST /users} instead of leaving it open, so there is
     * no second authentication scheme and no hole punched for registration.
     */
    public String issueForService() {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(SERVICE_TOKEN_TTL))
                .subject(issuer)
                .claim(CLAIM_ROLE, SERVICE_ROLE)
                .build();

        return sign(claims);
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
