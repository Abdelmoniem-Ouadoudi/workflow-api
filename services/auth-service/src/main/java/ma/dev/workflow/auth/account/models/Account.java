package ma.dev.workflow.auth.account.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One login. No email, no display name, no profile: those belong to work-service's app_user,
 * and duplicating them here would create a second source of truth with no owner.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt output is 60 characters. The column is 72 so a cost or algorithm change fits. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * The app_user id in work-service. Unique, but not a foreign key: it points into another
     * database, so nothing can enforce it here. That is the service boundary, made visible.
     */
    @Column(name = "work_user_id", nullable = false, unique = true)
    private Long workUserId;

    /**
     * Whether this account may log in, and why not when it may not.
     *
     * <p>This column is the authority. work-service keeps a boolean {@code app_user.is_active} so
     * it can filter an assignee dropdown without a network call, but that is a copy: a login is
     * answered here, by one service, with no hop to anywhere.
     *
     * <p>New accounts start PENDING. Deletion is never an option — {@code issue.reporter_id} is
     * ON DELETE RESTRICT precisely so history survives the person.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
