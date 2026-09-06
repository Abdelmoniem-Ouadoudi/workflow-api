package ma.dev.workflow.project.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.project.models.enums.JoinRequestStatus;
import ma.dev.workflow.user.models.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Somebody was given a project's join code and is asking to be let in.
 *
 * <p>The code alone does not admit anybody. It proves they were told about the project by someone
 * who had it; a project manager still decides. That matters because a mailed code gets forwarded.
 */
@Entity
@Table(name = "project_join_request")
@Getter
@Setter
public class ProjectJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** Null while pending. A request that nobody has decided has no decision time. */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /**
     * Who decided. Null while pending, for the same reason: making it required would need a
     * placeholder user meaning "nobody", which is a lie in the schema told to avoid a null in Java.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;
}
