package ma.dev.workflow.board.service;

import ma.dev.workflow.board.dto.BoardDTO;
import ma.dev.workflow.board.dto.BoardViewDTO;
import ma.dev.workflow.issue.dto.IssueSummaryDTO;

import java.util.List;

public interface IBoardService {

    /** The Kanban read: every status column, filled according to the board type. */
    BoardViewDTO getView(Long boardId);

    /** Issues on the board with no sprint. */
    List<IssueSummaryDTO> getBacklog(Long boardId);

    List<BoardDTO> findAll();

    List<BoardDTO> findByProjectId(Long projectId);

    BoardDTO findById(Long id);

    BoardDTO create(BoardDTO dto);

    BoardDTO update(Long id, BoardDTO dto);

    void deleteById(Long id);
}
