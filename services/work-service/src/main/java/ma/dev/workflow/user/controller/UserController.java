package ma.dev.workflow.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.dev.workflow.user.dto.UserDTO;
import ma.dev.workflow.user.models.enums.Role;
import ma.dev.workflow.user.service.IUserService;
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
@RequestMapping("/users")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDTO> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public UserDTO findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO create(@Valid @RequestBody UserDTO dto) {
        return userService.create(dto);
    }

    @PutMapping("/{id}")
    public UserDTO update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        return userService.update(id, dto);
    }

    /**
     * Mirrors the global role. SERVICE-only, so a browser cannot reach it.
     *
     * <p>Its own endpoint rather than a field on {@code PUT /users/{id}}, because the two have
     * different owners: a person may edit their username, but only auth-service may say what
     * somebody's role is, since auth-service is what stamps it into tokens.
     */
    @PutMapping("/{id}/role")
    public UserDTO setRole(@PathVariable Long id, @Valid @RequestBody SetRoleRequest request) {
        return userService.setRole(id, request.role());
    }

    /** Mirrors the account status after an administrator decided. SERVICE-only. */
    @PutMapping("/{id}/active")
    public UserDTO setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return userService.setActive(id, request.active());
    }

    /** Deactivation, not deletion. Returns the updated user rather than 204. */
    @DeleteMapping("/{id}")
    public UserDTO deactivate(@PathVariable Long id) {
        return userService.deactivate(id);
    }

    public record SetRoleRequest(@NotNull(message = "Role is required") Role role) {
    }

    public record SetActiveRequest(@NotNull(message = "Active is required") Boolean active) {
    }
}
