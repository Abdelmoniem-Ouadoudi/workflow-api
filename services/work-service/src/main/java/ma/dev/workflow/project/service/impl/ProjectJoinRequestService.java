package ma.dev.workflow.project.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.common.security.ProjectAccess;
import ma.dev.workflow.project.dto.JoinRequestDTO;
import ma.dev.workflow.project.dto.ProjectLookupDTO;
import ma.dev.workflow.project.dto.mapper.JoinRequestMapper;
import ma.dev.workflow.project.models.Project;
import ma.dev.workflow.project.models.ProjectJoinRequest;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.JoinRequestStatus;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectJoinRequestRepository;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.project.repositories.ProjectRepository;
import ma.dev.workflow.project.service.IProjectJoinRequestService;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The way in for somebody who was mailed a project's join code.
 *
 * <p>The code does not admit anybody by itself. It proves the person was told about the project by
 * someone who had the code; a project manager still decides. That distinction is the point: a code
 * sent by mail gets forwarded, quoted in a reply-all, and pasted into a chat, and none of those
 * should put a stranger on the project.
 */
@Service
@Transactional(readOnly = true)
public class ProjectJoinRequestService implements IProjectJoinRequestService {

    private final ProjectRepository projectRepository;
    private final ProjectJoinRequestRepository requestRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final JoinRequestMapper requestMapper;
    private final ProjectAccess projectAccess;

    public ProjectJoinRequestService(ProjectRepository projectRepository,
                                     ProjectJoinRequestRepository requestRepository,
                                     ProjectMemberRepository memberRepository,
                                     UserRepository userRepository,
                                     JoinRequestMapper requestMapper,
                                     ProjectAccess projectAccess) {
        this.projectRepository = projectRepository;
        this.requestRepository = requestRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.requestMapper = requestMapper;
        this.projectAccess = projectAccess;
    }

    /**
     * A lookup by an unguessable value, not a search. Twelve characters of a 32-symbol alphabet is
     * about 60 bits, so nothing here reveals a project to somebody who was not given its code.
     */
    @Override
    public ProjectLookupDTO lookupByJoinCode(String joinCode) {
        Project project = requireProjectByCode(joinCode);
        return new ProjectLookupDTO(project.getId(), project.getKey(), project.getName());
    }

    @Override
    @Transactional
    public JoinRequestDTO request(Long projectId, String joinCode) {
        Project project = requireProjectByCode(joinCode);

        // The id in the URL and the project the code opens must be the same one. Without this a
        // valid code for project A would let somebody ask to join project B.
        if (!project.getId().equals(projectId)) {
            throw new BusinessRuleException("JOIN_CODE_MISMATCH",
                    "That code does not belong to this project.");
        }

        Long userId = projectAccess.currentUserId();
        if (memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessRuleException("ALREADY_A_MEMBER",
                    "You are already on this project.");
        }

        // Readable message in the normal case. uq_join_request_pending is what actually holds when
        // an impatient double-click sends the same request twice.
        requestRepository.findByProjectIdAndUserIdAndStatus(projectId, userId, JoinRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("REQUEST_ALREADY_PENDING",
                            "You have already asked to join this project.");
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        ProjectJoinRequest request = new ProjectJoinRequest();
        request.setProject(project);
        request.setUser(user);
        request.setStatus(JoinRequestStatus.PENDING);
        return requestMapper.fromModel(requestRepository.save(request));
    }

    @Override
    public List<JoinRequestDTO> myRequests() {
        return requestMapper.fromModelList(
                requestRepository.findByUserIdOrderByRequestedAtDesc(projectAccess.currentUserId()));
    }

    @Override
    public List<JoinRequestDTO> pendingFor(Long projectId) {
        projectAccess.requireProjectManager(projectId);
        return requestMapper.fromModelList(
                requestRepository.findByProjectIdAndStatus(projectId, JoinRequestStatus.PENDING));
    }

    /** Says yes and puts the person on the project, in one transaction. */
    @Override
    @Transactional
    public JoinRequestDTO approve(Long projectId, Long requestId) {
        projectAccess.requireProjectManager(projectId);
        ProjectJoinRequest request = requirePending(projectId, requestId);

        // Approving is two writes that must not come apart: a request marked APPROVED with no
        // membership row is a person who has been told yes and still cannot see the project.
        ProjectMember member = new ProjectMember();
        member.setProject(request.getProject());
        member.setUser(request.getUser());
        member.setRole(ProjectRole.MEMBER);
        memberRepository.save(member);

        return requestMapper.fromModel(settle(request, JoinRequestStatus.APPROVED));
    }

    @Override
    @Transactional
    public JoinRequestDTO reject(Long projectId, Long requestId) {
        projectAccess.requireProjectManager(projectId);
        // The row is kept, not deleted. Who asked and who said no is the record, and it is what a
        // second request from the same person is checked against.
        return requestMapper.fromModel(
                settle(requirePending(projectId, requestId), JoinRequestStatus.REJECTED));
    }

    private ProjectJoinRequest settle(ProjectJoinRequest request, JoinRequestStatus status) {
        Long deciderId = projectAccess.currentUserId();
        User decider = userRepository.findById(deciderId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + deciderId));
        request.setStatus(status);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decider);
        return requestRepository.save(request);
    }

    private ProjectJoinRequest requirePending(Long projectId, Long requestId) {
        ProjectJoinRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Join request not found: " + requestId));

        // The request must belong to the project in the URL, or a manager of one project could
        // decide requests belonging to another.
        if (!request.getProject().getId().equals(projectId)) {
            throw new EntityNotFoundException("Join request not found: " + requestId);
        }
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BusinessRuleException("REQUEST_ALREADY_DECIDED",
                    "This request has already been " + request.getStatus().name().toLowerCase() + ".");
        }
        return request;
    }

    private Project requireProjectByCode(String joinCode) {
        return projectRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new EntityNotFoundException("No project uses that join code."));
    }
}
