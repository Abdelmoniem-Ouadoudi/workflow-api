package ma.dev.workflow.sprint.dto.mapper;

import ma.dev.workflow.sprint.dto.SprintDTO;
import ma.dev.workflow.sprint.models.Sprint;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SprintMapper {

    @Mapping(source = "board.id", target = "boardId")
    SprintDTO fromModel(Sprint model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Sprint fromDTO(SprintDTO dto);

    List<SprintDTO> fromModelList(List<Sprint> models);
}
