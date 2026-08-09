package ma.dev.workflow.board.repositories;

import ma.dev.workflow.board.models.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProjectId(Long projectId);

    boolean existsByProjectId(Long projectId);
}
