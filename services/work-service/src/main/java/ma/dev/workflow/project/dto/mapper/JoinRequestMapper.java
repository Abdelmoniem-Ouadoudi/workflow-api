package ma.dev.workflow.project.dto.mapper;

import ma.dev.workflow.project.dto.JoinRequestDTO;
import ma.dev.workflow.project.models.ProjectJoinRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JoinRequestMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "project.key", target = "projectKey")
    @Mapping(source = "project.name", target = "projectName")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    JoinRequestDTO fromModel(ProjectJoinRequest model);

    List<JoinRequestDTO> fromModelList(List<ProjectJoinRequest> models);
}
