package ma.dev.workflow.board.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.board.dto.BoardDTO;
import ma.dev.workflow.board.dto.BoardViewDTO;
import ma.dev.workflow.board.service.IBoardService;
import ma.dev.workflow.issue.dto.IssueSummaryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/boards")
public class BoardController {

    private final IBoardService boardService;

    public BoardController(IBoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public List<BoardDTO> findAll(@RequestParam(required = false) Long projectId) {
        return projectId == null ? boardService.findAll() : boardService.findByProjectId(projectId);
    }

    @GetMapping("/{id}")
    public BoardDTO findById(@PathVariable Long id) {
        return boardService.findById(id);
    }

    /** What the Kanban page loads. */
    @GetMapping("/{id}/view")
    public BoardViewDTO getView(@PathVariable Long id) {
        return boardService.getView(id);
    }

    @GetMapping("/{id}/backlog")
    public List<IssueSummaryDTO> getBacklog(@PathVariable Long id) {
        return boardService.getBacklog(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardDTO create(@Valid @RequestBody BoardDTO dto) {
        return boardService.create(dto);
    }

    @PutMapping("/{id}")
    public BoardDTO update(@PathVariable Long id, @Valid @RequestBody BoardDTO dto) {
        return boardService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable Long id) {
        boardService.deleteById(id);
    }
}
