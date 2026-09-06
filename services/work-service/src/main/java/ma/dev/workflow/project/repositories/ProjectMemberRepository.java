package ma.dev.workflow.project.repositories;

import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findByProjectId(Long projectId);

    long countByProjectIdAndRole(Long projectId, ProjectRole role);

    /**
     * The ids of every project this person is on.
     *
     * <p>Projected to ids rather than entities on purpose: this runs on nearly every request, and
     * all the caller ever asks is "is this id in the set". Loading whole rows to throw them away
     * would be the most expensive query in the system by call count.
     */
    @Query("select pm.project.id from ProjectMember pm where pm.user.id = :userId")
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);
}
