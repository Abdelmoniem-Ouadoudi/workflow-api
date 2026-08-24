package ma.dev.workflow.auth.common.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * One symmetric key, used to sign here and to verify in the gateway and in work-service.
 *
 * <p>The trade-off, said out loud: any service holding this secret could also mint a token, so the
 * key is only as trustworthy as the least trustworthy service holding it. That is acceptable when
 * all four deploy together from one repository. Production splits it into RS256 — the private key
 * stays here, everyone else gets the public half — and not one line of the verifying code changes,
 * because Spring Security validates a JWT the same way either way.
 */
@Configuration
public class JwtConfig {

    /** HS256 needs a key of at least 256 bits, so 32 bytes of UTF-8. Anything shorter is rejected. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " characters for HS256, got "
                            + keyBytes.length + ". Set the JWT_SECRET environment variable.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /** Signs. Only this service has one. */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * Verifies. This service needs one too, because {@code GET /auth/me} reads a token it issued.
     * The algorithm is pinned to HS256: leaving it open would let a caller present a token signed
     * with an algorithm we never intended to accept.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
