package ma.dev.workflow.project.service;

import ma.dev.workflow.project.dto.AddMemberRequest;
import ma.dev.workflow.project.dto.ProjectMemberDTO;
import ma.dev.workflow.project.models.enums.ProjectRole;

import java.util.List;

public interface IProjectMemberService {

    List<ProjectMemberDTO> findByProject(Long projectId);

    ProjectMemberDTO add(Long projectId, AddMemberRequest request);

    ProjectMemberDTO changeRole(Long projectId, Long userId, ProjectRole role);

    void remove(Long projectId, Long userId);

    /** The join code, for the project manager to send to somebody. */
    String joinCode(Long projectId);

    /** Replaces the join code. Members stay members and open requests stay open. */
    String rotateJoinCode(Long projectId);
}
