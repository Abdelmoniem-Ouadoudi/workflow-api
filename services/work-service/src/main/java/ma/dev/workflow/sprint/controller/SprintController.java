package ma.dev.workflow.sprint.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.sprint.dto.SprintDTO;
import ma.dev.workflow.sprint.service.ISprintService;
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
@RequestMapping("/sprints")
public class SprintController {

    private final ISprintService sprintService;

    public SprintController(ISprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping
    public List<SprintDTO> findAll(@RequestParam(required = false) Long boardId) {
        return boardId == null ? sprintService.findAll() : sprintService.findByBoardId(boardId);
    }

    @GetMapping("/{id}")
    public SprintDTO findById(@PathVariable Long id) {
        return sprintService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SprintDTO create(@Valid @RequestBody SprintDTO dto) {
        return sprintService.create(dto);
    }

    @PutMapping("/{id}")
    public SprintDTO update(@PathVariable Long id, @Valid @RequestBody SprintDTO dto) {
        return sprintService.update(id, dto);
    }

    @PostMapping("/{id}/start")
    public SprintDTO start(@PathVariable Long id) {
        return sprintService.start(id);
    }

    @PostMapping("/{id}/complete")
    public SprintDTO complete(@PathVariable Long id) {
        return sprintService.complete(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable Long id) {
        sprintService.deleteById(id);
    }
}
