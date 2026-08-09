package ma.dev.workflow.sprint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.sprint.models.enums.SprintState;

import java.time.LocalDate;

@Getter
@Setter
public class SprintDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private String goal;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Changed only through /sprints/{id}/start and /complete, never through PUT. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private SprintState state;

    @NotNull(message = "Board id is required")
    private Long boardId;
}
