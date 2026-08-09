package ma.dev.workflow.user.dto.mapper;

import ma.dev.workflow.user.dto.UserDTO;
import ma.dev.workflow.user.models.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDTO fromModel(User model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User fromDTO(UserDTO dto);

    List<UserDTO> fromModelList(List<User> models);
}
