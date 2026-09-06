package ma.dev.workflow.project.repositories;

import ma.dev.workflow.project.models.ProjectJoinRequest;
import ma.dev.workflow.project.models.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectJoinRequestRepository extends JpaRepository<ProjectJoinRequest, Long> {

    List<ProjectJoinRequest> findByProjectIdAndStatus(Long projectId, JoinRequestStatus status);

    Optional<ProjectJoinRequest> findByProjectIdAndUserIdAndStatus(Long projectId, Long userId,
                                                                  JoinRequestStatus status);

    /** What the person themselves sees: every project they have asked about, settled or not. */
    List<ProjectJoinRequest> findByUserIdOrderByRequestedAtDesc(Long userId);
}
