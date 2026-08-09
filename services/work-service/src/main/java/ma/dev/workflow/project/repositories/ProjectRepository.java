package ma.dev.workflow.project.repositories;

import jakarta.persistence.LockModeType;
import ma.dev.workflow.project.models.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByKey(String key);

    boolean existsByKey(String key);

    /**
     * SELECT ... FOR UPDATE. Locks the project row until the transaction ends, so two
     * concurrent issue creations in the same project cannot read the same counter value.
     * Without this, both would read 7 and both would build WORK-8.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
