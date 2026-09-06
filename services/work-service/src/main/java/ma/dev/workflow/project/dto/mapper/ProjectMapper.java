package ma.dev.workflow.project.dto.mapper;

import ma.dev.workflow.project.dto.ProjectDTO;
import ma.dev.workflow.project.models.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectDTO fromModel(Project model);

    /**
     * The id and the timestamps are owned by the server.
     * Ignoring them here means a client cannot set them by sending them in the body.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // The join code is a secret the server makes. ProjectDTO has no field for it on purpose:
    // it is not in the project list every member reads, and it cannot be set from a request body.
    // Only GET /projects/{id}/join-code returns it, and only to a project manager.
    @Mapping(target = "joinCode", ignore = true)
    Project fromDTO(ProjectDTO dto);

    List<ProjectDTO> fromModelList(List<Project> models);
}
