package ma.dev.workflow.issue_attachment.dto.mapper;

import ma.dev.workflow.issue_attachment.dto.IssueAttachmentDTO;
import ma.dev.workflow.issue_attachment.models.IssueAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueAttachmentMapper {

    @Mapping(source = "issue.id", target = "issueId")
    IssueAttachmentDTO fromModel(IssueAttachment model);

    List<IssueAttachmentDTO> fromModelList(List<IssueAttachment> models);
}
