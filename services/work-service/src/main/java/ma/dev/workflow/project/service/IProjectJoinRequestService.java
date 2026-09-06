package ma.dev.workflow.project.service;

import ma.dev.workflow.project.dto.JoinRequestDTO;
import ma.dev.workflow.project.dto.ProjectLookupDTO;

import java.util.List;

public interface IProjectJoinRequestService {

    /** Turns a code into the project it opens, so the asker can check before asking. */
    ProjectLookupDTO lookupByJoinCode(String joinCode);

    JoinRequestDTO request(Long projectId, String joinCode);

    /** Every project the caller has asked about, settled or not. */
    List<JoinRequestDTO> myRequests();

    List<JoinRequestDTO> pendingFor(Long projectId);

    JoinRequestDTO approve(Long projectId, Long requestId);

    JoinRequestDTO reject(Long projectId, Long requestId);
}
