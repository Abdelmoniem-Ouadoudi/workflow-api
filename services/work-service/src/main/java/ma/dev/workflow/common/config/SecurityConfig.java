package ma.dev.workflow.common.config;

import ma.dev.workflow.common.exception.ApiErrorWriter;
import ma.dev.workflow.common.security.TokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * This service verifies tokens. It never issues one.
 *
 * <p>The gateway already rejects an unauthenticated request, so this chain looks redundant. It is
 * not: this service listens on 8081 and anything on the network can call it directly. The gateway
 * is where a bad request fails fast, not where the system is secured. A jury asking "what stops me
 * calling 8081 directly" gets an answer that is a demonstration, not a promise.
 */
@Configuration
public class SecurityConfig {

    /** HS256 needs a key of at least 256 bits, so 32 bytes of UTF-8. */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * The same secret auth-service signs with. Symmetric, so this service could in principle also
     * mint a token: acceptable while all four services deploy together from one repository, and
     * the reason the production answer is RS256 with the private key held only by auth-service.
     * Nothing in the verifying code would change.
     */
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
                // Pinned. Leaving the algorithm open would accept a token signed with one this
                // service never meant to trust.
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Checks expiry, and that the token came from our issuer and not from some other system
        // that happens to share a secret.
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators
                .createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Turns the {@code role} claim into an authority.
     *
     * <p>Spring's default reads {@code scope} or {@code scp} and prefixes {@code SCOPE_}, which is
     * the OAuth2 convention and not what auth-service issues. This mapping is what makes
     * {@code hasRole("ADMIN")} below work.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLE);
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
                // No CORS configuration here at all, and that is the fix for a real bug.
                //
                // M1 needed it: the browser called this service directly on 8081. M2 put the
                // gateway in front, and the gateway forwards the downstream response headers
                // through - so a browser calling /projects received Access-Control-Allow-Origin
                // twice, once from the gateway and once from here. A duplicated
                // Access-Control-Allow-Origin is rejected outright, and the fetch fails as if the
                // server were unreachable. curl does not enforce CORS, so the whole smoke suite
                // passed while the app was broken in the browser.
                //
                // CORS is a browser concern, and the browser only ever talks to the gateway.
                // Swagger UI on 8081 needs nothing here: it is served from this same origin, so
                // its calls are not cross-origin.
                //
                // No cookies and no session: CSRF defends a credential the browser attaches on
                // its own, and a bearer token is attached by our code. Nothing to defend.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // The contract stays readable without a token. It documents shapes, not data.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Spring forwards an unhandled error to /error as a second, internal
                        // dispatch, and the security filters run on it too. Without this, every
                        // such error is reported as 401 "a valid token is required" no matter what
                        // actually went wrong - the authenticated caller's 404 comes back as a lie.
                        .requestMatchers("/error").permitAll()
                        // Creating a profile is auth-service's job, done with a service token
                        // during registration. There is deliberately no second way to create a
                        // person, because that would produce a profile nobody can log in as.
                        .requestMatchers(HttpMethod.POST, "/users").hasRole(TokenClaims.SERVICE_ROLE)
                        // The global role and the account status are auth-service's to decide,
                        // because auth-service is what stamps a role into a token and what answers
                        // a login. These two endpoints exist so it can mirror its decision here,
                        // and they are reachable with a service token only - never from a browser.
                        .requestMatchers(HttpMethod.PUT, "/users/*/role").hasRole(TokenClaims.SERVICE_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/users/*/active").hasRole(TokenClaims.SERVICE_ROLE)
                        // Reading the user list means reading everybody's email address. Before M5
                        // any signed-in caller could, because the board's assignee dropdown needed
                        // it; that dropdown now reads GET /projects/{id}/members instead. SERVICE
                        // is here because auth-service builds its admin screen from this list.
                        .requestMatchers(HttpMethod.GET, "/users", "/users/*")
                                .hasAnyRole("ADMIN", TokenClaims.SERVICE_ROLE)
                        // Deactivating someone is an administrator's decision.
                        .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")
                        // Operational actions, not work. Reindexing pushes every issue back through
                        // the classifier, which is a real load on an external model.
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // Without these two, a refusal inside the filter chain comes back with an empty
                // body, and the React error handler is written against the ApiError shape.
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
