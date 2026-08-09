package ma.dev.workflow.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectDTO {

    /** Assigned by the database. Sent in responses, ignored in requests. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Project key is required")
    @Pattern(
            regexp = "^[A-Z][A-Z0-9]{1,9}$",
            message = "Project key must be 2 to 10 characters, upper-case letters or digits, starting with a letter")
    private String key;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private String description;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
}
