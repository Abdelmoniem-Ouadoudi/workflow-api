package ma.dev.workflow.auth.common.config;

import ma.dev.workflow.auth.account.service.impl.AccountDetailsService;
import ma.dev.workflow.auth.common.exception.ApiErrorWriter;
import ma.dev.workflow.auth.token.JwtIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * BCrypt, with the default strength of 10.
     *
     * <p>It is deliberately slow and it salts every hash on its own, so two people with the same
     * password get different rows and a stolen table cannot be attacked with a precomputed
     * dictionary. The cost factor is stored inside the hash string, which is why raising it later
     * does not invalidate existing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * One provider, built explicitly rather than pulled out of {@code AuthenticationConfiguration}
     * so that the chain is visible: load the account, match the hash, check the account is enabled.
     */
    @Bean
    public AuthenticationManager authenticationManager(AccountDetailsService accountDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accountDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Turns the token's {@code role} claim into a Spring Security authority.
     *
     * <p>Spring's default reads {@code scope} or {@code scp} and prefixes with {@code SCOPE_},
     * which is the OAuth2 convention and not what this system issues. Mapping to
     * {@code ROLE_<value>} is what makes {@code hasRole("ADMIN")} work, here and in work-service,
     * which uses an identical converter.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtIssuer.CLAIM_ROLE);
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
                // No cookies and no session, so there is no session for a forged form to ride on.
                // CSRF protection exists to defend a cookie the browser attaches automatically;
                // a bearer token is attached by our own code, so there is nothing to defend here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The two doors into the system. They cannot require a token: the whole
                        // point of them is that the caller does not have one yet.
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Spring forwards an unhandled error to /error as a second, internal
                        // dispatch, and the security filters run on it too. Without this, every
                        // such error is reported as 401 "a valid token is required" no matter what
                        // actually went wrong.
                        .requestMatchers("/error").permitAll()
                        // A person, not a service. The short-lived SERVICE token this application
                        // mints for its own call to work-service has no user behind it and no uid
                        // claim, so it is refused here rather than being allowed to read nothing.
                        .requestMatchers("/auth/me").hasAnyRole("DEVELOPER", "MANAGER", "ADMIN")
                        // Who exists on the platform, what they are, and whether they may log in.
                        // Not who is on which project - that is the project manager's, and it
                        // lives in work-service where the projects are.
                        .requestMatchers("/admin/accounts/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Needed for GET /auth/me: this service verifies the tokens it issued.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // Without these two, a refusal inside the filter chain comes back with an empty
                // body and the React error handler has nothing to read.
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
