package ma.dev.workflow.project.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.project.dto.ProjectDTO;
import ma.dev.workflow.project.service.IProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final IProjectService projectService;

    public ProjectController(IProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectDTO> findAll() {
        return projectService.findAll();
    }

    @GetMapping("/{id}")
    public ProjectDTO findById(@PathVariable Long id) {
        return projectService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDTO create(@Valid @RequestBody ProjectDTO dto) {
        return projectService.create(dto);
    }

    @PutMapping("/{id}")
    public ProjectDTO update(@PathVariable Long id, @Valid @RequestBody ProjectDTO dto) {
        return projectService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable Long id) {
        projectService.deleteById(id);
    }
}
