package ma.dev.workflow.issue_status_change.dto.mapper;

import ma.dev.workflow.issue_status_change.dto.IssueStatusChangeDTO;
import ma.dev.workflow.issue_status_change.models.IssueStatusChange;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueStatusChangeMapper {

    @Mapping(source = "issue.id", target = "issueId")
    @Mapping(source = "changedBy.id", target = "changedById")
    @Mapping(source = "changedBy.username", target = "changedByUsername")
    IssueStatusChangeDTO fromModel(IssueStatusChange model);

    List<IssueStatusChangeDTO> fromModelList(List<IssueStatusChange> models);
}
