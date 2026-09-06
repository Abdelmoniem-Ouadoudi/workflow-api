package ma.dev.workflow.user.service;

import ma.dev.workflow.user.dto.UserDTO;
import ma.dev.workflow.user.models.enums.Role;

import java.util.List;

public interface IUserService {

    List<UserDTO> findAll();

    UserDTO findById(Long id);

    UserDTO create(UserDTO dto);

    /** Username and email only. The global role is owned by auth-service — see {@link #setRole}. */
    UserDTO update(Long id, UserDTO dto);

    /** Mirrors the role auth-service has just written. Called by auth-service, never by a browser. */
    UserDTO setRole(Long id, Role role);

    /** Mirrors the account status: false while PENDING or DISABLED, true once approved. */
    UserDTO setActive(Long id, boolean active);

    /** Deactivates instead of deleting: issues and comments keep pointing at a real row. */
    UserDTO deactivate(Long id);
}
