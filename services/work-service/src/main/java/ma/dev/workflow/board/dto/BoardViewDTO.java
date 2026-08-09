package ma.dev.workflow.board.dto;

import ma.dev.workflow.board.models.enums.BoardType;
import ma.dev.workflow.issue.dto.IssueSummaryDTO;
import ma.dev.workflow.issue.models.enums.Status;

import java.util.List;

/**
 * A derived read. No table matches this shape: it is assembled per request.
 * Every status column is always present, even when empty, so the UI does not have to guess.
 */
public record BoardViewDTO(
        Long boardId,
        String boardName,
        BoardType type,
        ActiveSprint activeSprint,
        List<Column> columns) {

    public record ActiveSprint(Long id, String name, String goal) {
    }

    public record Column(Status status, List<IssueSummaryDTO> issues) {
    }
}
