package ma.dev.workflow.project.dto;

import ma.dev.workflow.project.models.enums.JoinRequestStatus;

import java.time.LocalDateTime;

/**
 * A request to join, as the project manager's queue shows it and as the asker sees it come back.
 *
 * <p>Carries the project key and name as well as the asker, because the same record is read from
 * both ends: a manager looking at one project's queue needs the person, and a person looking at
 * what they have asked for needs the project.
 */
public record JoinRequestDTO(
        Long id,
        Long projectId,
        String projectKey,
        String projectName,
        Long userId,
        String username,
        String email,
        JoinRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt) {
}
