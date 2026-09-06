package ma.dev.workflow.project.dto;

import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.user.models.enums.Role;

import java.time.LocalDateTime;

/**
 * One person on a project, as the members screen shows them.
 *
 * <p>Flattened rather than nesting a {@code UserDTO}: the screen is a table, and every column it
 * draws is here. It carries both roles because they answer different questions and a manager
 * reading the list needs both — {@code globalRole} says whether this person could start a project
 * of their own, {@code role} says what they are on <em>this</em> one.
 *
 * <p>A record, unlike the older DTOs here, because nothing ever mutates one: it is only ever built
 * from a row and written to JSON.
 */
public record ProjectMemberDTO(
        Long userId,
        String username,
        String email,
        Role globalRole,
        ProjectRole role,
        boolean active,
        LocalDateTime joinedAt) {
}
