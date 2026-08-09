package ma.dev.workflow.issue_comment.dto.mapper;

import ma.dev.workflow.issue_comment.dto.IssueCommentDTO;
import ma.dev.workflow.issue_comment.models.IssueComment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueCommentMapper {

    @Mapping(source = "issue.id", target = "issueId")
    @Mapping(source = "author.id", target = "authorId")
    IssueCommentDTO fromModel(IssueComment model);

    List<IssueCommentDTO> fromModelList(List<IssueComment> models);
}
