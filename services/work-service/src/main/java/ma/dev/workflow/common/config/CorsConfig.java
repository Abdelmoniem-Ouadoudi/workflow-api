package ma.dev.workflow.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * The rules, in one place, as a bean.
     *
     * A CorsConfigurationSource bean on its own does nothing in plain Spring MVC - only Spring
     * Security's http.cors() looks one up. It is declared as a bean anyway because M2 adds that
     * security chain and will consume this exact bean, which is what keeps the CORS rules and the
     * security rules from drifting apart.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit origins, never "*": the spec forbids combining "*" with allowCredentials,
        // and the browser rejects the pair.
        configuration.setAllowedOrigins(allowedOrigins);
        // OPTIONS is listed because the browser sends an unauthenticated preflight before any
        // PATCH and before any request carrying a header. Omitting it breaks the board at M2.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * What actually applies those rules today, since there is no security chain yet.
     * Ordered first so the preflight is answered before anything else runs.
     *
     * At M2 the SecurityFilterChain installs its own CORS filter from the bean above.
     * This registration is removed then: two filters would write
     * Access-Control-Allow-Origin twice and the browser rejects a duplicated header.
     * Tracked in docs/BACKLOG.md.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            // Qualified by name: Spring MVC's own mvcHandlerMappingIntrospector also implements
            // CorsConfigurationSource, so the type alone is ambiguous.
            @Qualifier("corsConfigurationSource") CorsConfigurationSource source) {
        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
