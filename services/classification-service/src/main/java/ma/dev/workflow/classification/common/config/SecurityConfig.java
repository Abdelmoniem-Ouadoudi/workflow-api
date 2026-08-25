package ma.dev.workflow.classification.common.config;

import ma.dev.workflow.classification.common.exception.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * This service is driven by a queue, not by callers, so its HTTP surface is two admin endpoints
 * and a health check. Both admin endpoints still need defending: replaying a dead-letter queue
 * puts real work back into the system.
 *
 * <p>Same secret, same issuer check, same role mapping as work-service. One authentication scheme
 * across four services rather than a special case for the one that mostly does not serve HTTP.
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
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /** Maps the {@code role} claim to ROLE_&lt;value&gt;, identically to work-service. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           ApiErrorWriter apiErrorWriter) throws Exception {
        http
                // No CORS. The browser never calls this service - it talks only to the gateway,
                // which has no route here. Configuring CORS anyway is how work-service ended up
                // sending Access-Control-Allow-Origin twice.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Spring forwards an unhandled error to /error as a second dispatch and
                        // these filters run on it too. Without this a 404 comes back as a 401.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
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
