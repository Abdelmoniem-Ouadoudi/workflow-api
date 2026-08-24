package ma.dev.workflow.gateway.config;

import ma.dev.workflow.gateway.common.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * The half of M2's acceptance test that says "unauthenticated requests are rejected at the
 * gateway". An anonymous call to /projects stops here and never reaches work-service.
 *
 * <p>This is not the system's only lock. work-service verifies the same token again, because it
 * listens on its own port and nothing forces a caller to come through here. The gateway is where
 * a bad request fails cheaply.
 */
@Configuration
public class SecurityConfig {

    /** HS256 needs a key of at least 256 bits, so 32 bytes of UTF-8. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret,
                                 @Value("${app.jwt.issuer}") String issuer) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " characters for HS256, got "
                            + keyBytes.length + ". Set the JWT_SECRET environment variable.");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                // Pinned, so a token signed with an algorithm we never intended to trust is refused.
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Expiry, plus the issuer: a valid signature from some other system sharing a secret is
        // still not a token this system should honour.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           ApiErrorWriter apiErrorWriter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // No session and no cookie, so there is no credential the browser attaches by
                // itself and therefore nothing for CSRF protection to defend.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Registering and logging in cannot require a token: not having one is the
                        // whole reason a caller is there.
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Reached by an internal forward when a circuit breaker opens. The original
                        // request was already authorised, or it never got this far.
                        .requestMatchers("/fallback/**").permitAll()
                        // Spring forwards an unhandled error to /error as a second, internal
                        // dispatch, and these filters run on it too. Without this, a 404 for an
                        // unrouted path comes back as 401 "a valid token is required", which is
                        // not what went wrong.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // No role rules here on purpose. The gateway answers "is this a real caller".
                // Whether that caller may delete a user is a question only work-service can answer,
                // because only work-service knows what its endpoints mean.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                apiErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "A valid token is required."))
                        .accessDeniedHandler((request, response, deniedException) ->
                                apiErrorWriter.write(request, response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You are not allowed to do this.")));

        return http.build();
    }
}
