package ma.dev.workflow.project.service;

import ma.dev.workflow.project.dto.ProjectDTO;

import java.util.List;

public interface IProjectService {

    List<ProjectDTO> findAll();

    ProjectDTO findById(Long id);

    ProjectDTO create(ProjectDTO dto);

    ProjectDTO update(Long id, ProjectDTO dto);

    void deleteById(Long id);
}
