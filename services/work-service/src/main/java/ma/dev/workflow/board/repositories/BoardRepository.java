package ma.dev.workflow.board.repositories;

import ma.dev.workflow.board.models.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProjectId(Long projectId);

    /** Every board across the projects the caller belongs to. An empty list in means none out. */
    List<Board> findByProjectIdIn(List<Long> projectIds);

    boolean existsByProjectId(Long projectId);
}
