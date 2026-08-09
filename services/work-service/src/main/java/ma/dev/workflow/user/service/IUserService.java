package ma.dev.workflow.user.service;

import ma.dev.workflow.user.dto.UserDTO;

import java.util.List;

public interface IUserService {

    List<UserDTO> findAll();

    UserDTO findById(Long id);

    UserDTO create(UserDTO dto);

    UserDTO update(Long id, UserDTO dto);

    /** Deactivates instead of deleting: issues and comments keep pointing at a real row. */
    UserDTO deactivate(Long id);
}
