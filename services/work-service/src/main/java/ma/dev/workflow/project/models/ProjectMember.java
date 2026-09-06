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
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.user.models.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One person on one project, and what they are on it.
 *
 * <p>An association class rather than a plain join table: it carries a {@link ProjectRole}. A join
 * table would only say <em>that</em> somebody is on a project; this says <em>as what</em>.
 *
 * <p>The primary key is a surrogate id rather than {@code (project_id, user_id)}. That pair is
 * unique and could have been the key, but a composite key in JPA means an {@code @EmbeddedId} and
 * a separate id class, and every other entity here is a plain {@code Long id}. The unique
 * constraint gives the same guarantee for one line of YAML.
 */
@Entity
@Table(name = "project_member")
@Getter
@Setter
public class ProjectMember {

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
    private ProjectRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
