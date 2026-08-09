package ma.dev.workflow.issue_attachment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IssueAttachmentDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be at most 255 characters")
    private String fileName;

    @NotBlank(message = "File URL is required")
    @Size(max = 500, message = "File URL must be at most 500 characters")
    private String fileUrl;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be greater than zero")
    private Long fileSize;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long issueId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime uploadedAt;
}
