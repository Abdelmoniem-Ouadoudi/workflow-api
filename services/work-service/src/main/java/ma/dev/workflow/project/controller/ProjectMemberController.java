package ma.dev.workflow.project.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.project.dto.AddMemberRequest;
import ma.dev.workflow.project.dto.ChangeMemberRoleRequest;
import ma.dev.workflow.project.dto.ProjectMemberDTO;
import ma.dev.workflow.project.service.IProjectMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The chef de projet's screen: who is on the project, and the code that lets somebody ask to be.
 *
 * <p>Nested under {@code /projects/{projectId}} because a membership has no meaning without the
 * project it is on — and because the gateway's route list already covers {@code /projects/**},
 * so none of this needs a new entry there.
 */
@RestController
@RequestMapping("/projects/{projectId}")
public class ProjectMemberController {

    private final IProjectMemberService memberService;

    public ProjectMemberController(IProjectMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/members")
    public List<ProjectMemberDTO> findMembers(@PathVariable Long projectId) {
        return memberService.findByProject(projectId);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberDTO addMember(@PathVariable Long projectId,
                                      @Valid @RequestBody AddMemberRequest request) {
        return memberService.add(projectId, request);
    }

    @PutMapping("/members/{userId}")
    public ProjectMemberDTO changeMemberRole(@PathVariable Long projectId,
                                             @PathVariable Long userId,
                                             @Valid @RequestBody ChangeMemberRoleRequest request) {
        return memberService.changeRole(projectId, userId, request.role());
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        memberService.remove(projectId, userId);
    }

    @GetMapping("/join-code")
    public JoinCodeResponse joinCode(@PathVariable Long projectId) {
        return new JoinCodeResponse(memberService.joinCode(projectId));
    }

    @PostMapping("/join-code/rotate")
    public JoinCodeResponse rotateJoinCode(@PathVariable Long projectId) {
        return new JoinCodeResponse(memberService.rotateJoinCode(projectId));
    }

    /** Wrapped in an object rather than returned as a bare string, so it stays JSON. */
    public record JoinCodeResponse(String joinCode) {
    }
}
