package ma.dev.workflow.sprint.repositories;

import ma.dev.workflow.sprint.models.Sprint;
import ma.dev.workflow.sprint.models.enums.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByBoardId(Long boardId);

    Optional<Sprint> findByBoardIdAndState(Long boardId, SprintState state);
}
