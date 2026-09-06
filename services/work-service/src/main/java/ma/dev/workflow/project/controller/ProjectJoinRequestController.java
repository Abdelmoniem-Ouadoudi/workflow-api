package ma.dev.workflow.project.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.project.dto.JoinCodeRequest;
import ma.dev.workflow.project.dto.JoinRequestDTO;
import ma.dev.workflow.project.dto.ProjectLookupDTO;
import ma.dev.workflow.project.service.IProjectJoinRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectJoinRequestController {

    private final IProjectJoinRequestService joinRequestService;

    public ProjectJoinRequestController(IProjectJoinRequestService joinRequestService) {
        this.joinRequestService = joinRequestService;
    }

    /**
     * POST rather than GET, even though it reads.
     *
     * <p>The code is a secret, and a GET would put it in the query string — which lands in browser
     * history, in the gateway's access log, and in a {@code Referer} header on the next request.
     * A request body goes into none of those.
     */
    @PostMapping("/lookup")
    public ProjectLookupDTO lookup(@Valid @RequestBody JoinCodeRequest request) {
        return joinRequestService.lookupByJoinCode(request.joinCode());
    }

    @GetMapping("/join-requests/mine")
    public List<JoinRequestDTO> myRequests() {
        return joinRequestService.myRequests();
    }

    @PostMapping("/{projectId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRequestDTO request(@PathVariable Long projectId,
                                  @Valid @RequestBody JoinCodeRequest request) {
        return joinRequestService.request(projectId, request.joinCode());
    }

    @GetMapping("/{projectId}/join-requests")
    public List<JoinRequestDTO> pending(@PathVariable Long projectId) {
        return joinRequestService.pendingFor(projectId);
    }

    @PostMapping("/{projectId}/join-requests/{requestId}/approve")
    public JoinRequestDTO approve(@PathVariable Long projectId, @PathVariable Long requestId) {
        return joinRequestService.approve(projectId, requestId);
    }

    @PostMapping("/{projectId}/join-requests/{requestId}/reject")
    public JoinRequestDTO reject(@PathVariable Long projectId, @PathVariable Long requestId) {
        return joinRequestService.reject(projectId, requestId);
    }
}
