package ma.dev.workflow.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.board.models.enums.BoardType;

import java.time.LocalDateTime;

@Getter
@Setter
public class BoardDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @NotNull(message = "Board type is required")
    private BoardType type;

    /**
     * The foreign key as a plain id, not a nested ProjectDTO.
     * Nesting would force a join on every read and let the client depend on the whole graph.
     */
    @NotNull(message = "Project id is required")
    private Long projectId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
}
