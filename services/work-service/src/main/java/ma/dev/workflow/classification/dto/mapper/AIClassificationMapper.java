package ma.dev.workflow.classification.dto.mapper;

import ma.dev.workflow.classification.dto.AIClassificationDTO;
import ma.dev.workflow.classification.models.AIClassification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AIClassificationMapper {

    @Mapping(source = "issue.id", target = "issueId")
    // The issue's optimistic-locking version travels with the suggestion so the board can refresh
    // its cached copy after an auto-apply. Without it the next drag on that card sends a stale
    // version and gets a false 409.
    @Mapping(source = "issue.version", target = "issueVersion")
    AIClassificationDTO fromModel(AIClassification model);
}
