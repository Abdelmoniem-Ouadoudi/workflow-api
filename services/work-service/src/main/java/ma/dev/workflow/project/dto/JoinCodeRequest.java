package ma.dev.workflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The code somebody was given, on its way to being checked. */
public record JoinCodeRequest(

        @NotBlank(message = "Join code is required")
        @Size(max = 16, message = "Join code must be at most 16 characters")
        String joinCode) {
}
