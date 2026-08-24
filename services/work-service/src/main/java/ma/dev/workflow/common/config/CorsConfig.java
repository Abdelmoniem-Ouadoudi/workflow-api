package ma.dev.workflow.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * The rules, in one place, as a bean.
     *
     * <p>At M1 this bean did nothing on its own: plain Spring MVC ignores a
     * CorsConfigurationSource, and only Spring Security's http.cors() looks one up. A standalone
     * CorsFilter was registered alongside it to make the board work, with a note saying it had to
     * go at M2. M2 is here: SecurityConfig now calls http.cors(withDefaults()), which installs
     * Security's own filter from this bean, and the temporary registration has been deleted.
     * Keeping both would write Access-Control-Allow-Origin twice and the browser rejects a
     * duplicated header.
     *
     * <p>The browser normally talks to the gateway, which has its own copy of these rules. This
     * one still earns its place: Swagger UI and any direct call to 8081 during debugging go
     * through here.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit origins, never "*": the spec forbids combining "*" with allowCredentials,
        // and the browser rejects the pair.
        configuration.setAllowedOrigins(allowedOrigins);
        // OPTIONS is listed because the browser sends an unauthenticated preflight before any
        // PATCH and before any request carrying an Authorization header. Omitting it breaks
        // every write the board makes.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
