package ma.dev.workflow.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc reads the same RequestMappingHandlerMapping that Spring MVC already built,
 * walks every @RestController, and inspects the DTO types by reflection. The jakarta.validation
 * annotations come along: @NotBlank becomes required, @Size(max=150) becomes maxLength.
 * So the validation rules are also the frontend's type information, and the two cannot drift.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Work Service API")
                .version("v1")
                .description("Projects, boards, sprints and issues for the work management platform."));
    }
}
