package ma.dev.workflow.issue.dto.mapper;

import ma.dev.workflow.issue.dto.IssueDTO;
import ma.dev.workflow.issue.models.Issue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "board.id", target = "boardId")
    @Mapping(source = "sprint.id", target = "sprintId")
    @Mapping(source = "reporter.id", target = "reporterId")
    @Mapping(source = "assignee.id", target = "assigneeId")
    IssueDTO fromModel(Issue model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "issueKey", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "sprint", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Issue fromDTO(IssueDTO dto);

    List<IssueDTO> fromModelList(List<Issue> models);
}
