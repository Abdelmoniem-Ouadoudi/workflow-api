package ma.dev.workflow.sprint.service;

import ma.dev.workflow.sprint.dto.SprintDTO;

import java.util.List;

public interface ISprintService {

    List<SprintDTO> findAll();

    List<SprintDTO> findByBoardId(Long boardId);

    SprintDTO findById(Long id);

    SprintDTO create(SprintDTO dto);

    SprintDTO update(Long id, SprintDTO dto);

    /** PLANNED to ACTIVE. Fails if another sprint on the same board is already active. */
    SprintDTO start(Long id);

    /** ACTIVE to COMPLETED. Unfinished issues go back to the backlog in the same transaction. */
    SprintDTO complete(Long id);

    void deleteById(Long id);
}
