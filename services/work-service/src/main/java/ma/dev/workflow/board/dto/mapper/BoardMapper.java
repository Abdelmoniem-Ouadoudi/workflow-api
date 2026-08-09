package ma.dev.workflow.board.dto.mapper;

import ma.dev.workflow.board.dto.BoardDTO;
import ma.dev.workflow.board.models.Board;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BoardMapper {

    /** Reading only the id of a lazy association does not trigger a database load. */
    @Mapping(source = "project.id", target = "projectId")
    BoardDTO fromModel(Board model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Board fromDTO(BoardDTO dto);

    List<BoardDTO> fromModelList(List<Board> models);
}
