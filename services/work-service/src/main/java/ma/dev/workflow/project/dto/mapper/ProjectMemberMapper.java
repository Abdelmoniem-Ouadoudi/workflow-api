package ma.dev.workflow.project.dto.mapper;

import ma.dev.workflow.project.dto.ProjectMemberDTO;
import ma.dev.workflow.project.models.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Flattens a membership and the person it points at into one row of the members table.
 *
 * <p>One direction only. A {@code ProjectMember} is never built from a request body: the project
 * comes from the URL, the user from an id the service looks up, and {@code joinedAt} from the
 * database. There is nothing for a client to send.
 */
@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.role", target = "globalRole")
    @Mapping(source = "user.active", target = "active")
    ProjectMemberDTO fromModel(ProjectMember model);

    List<ProjectMemberDTO> fromModelList(List<ProjectMember> models);
}
