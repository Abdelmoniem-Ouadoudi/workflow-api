package ma.dev.workflow.project.repositories;

import jakarta.persistence.LockModeType;
import ma.dev.workflow.project.models.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByKey(String key);

    boolean existsByKey(String key);

    /**
     * Looking a project up by the secret somebody was mailed, without knowing its id.
     *
     * <p>This is a lookup by an unguessable value, not a search: nothing here reveals a project to
     * a caller who was not told the code.
     */
    Optional<Project> findByJoinCode(String joinCode);

    /**
     * Every project id, for the one caller who may see all of them.
     *
     * <p>Ids rather than rows: the dashboard needs them only to put in a {@code where ... in}, and
     * loading whole projects to read one column off each would be waste on every admin page load.
     */
    @Query("select p.id from Project p")
    List<Long> findAllIds();

    /**
     * SELECT ... FOR UPDATE. Locks the project row until the transaction ends, so two
     * concurrent issue creations in the same project cannot read the same counter value.
     * Without this, both would read 7 and both would build WORK-8.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
