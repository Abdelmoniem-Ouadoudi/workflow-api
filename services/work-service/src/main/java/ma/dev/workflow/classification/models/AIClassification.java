package ma.dev.workflow.classification.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ma.dev.workflow.classification.models.enums.Effort;
import ma.dev.workflow.classification.models.enums.ReviewStatus;
import ma.dev.workflow.issue.models.Issue;
import ma.dev.workflow.issue.models.enums.IssueType;
import ma.dev.workflow.issue.models.enums.Priority;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the model thought about one issue.
 *
 * <p>It lives here, in work-service's database, next to the issue it describes — not in
 * classification-service. The class diagram says {@code Issue "1" *-- "0..1" AIClassification},
 * composition: the suggestion is part of the issue's record and dies with it. That also leaves
 * classification-service holding no state at all, which is why more than one of it could run.
 *
 * <p>Every suggested field is nullable. A model with no opinion about the priority should leave it
 * empty rather than guess, and a null here means exactly that. {@code confidence} is the one field
 * always present, because it is what decides whether any of the others are acted on.
 */
@Entity
@Table(name = "ai_classification")
@Getter
@Setter
public class AIClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One per issue, enforced by a unique constraint on the column.
     *
     * <p>The uniqueness is not just modelling. RabbitMQ delivers at least once, so the same
     * classification can arrive twice; the listener updates the existing row instead of inserting,
     * and this constraint is the backstop if two deliveries ever race.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, unique = true)
    private Issue issue;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_type", length = 20)
    private IssueType suggestedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_priority", length = 20)
    private Priority suggestedPriority;

    /**
     * Free text, not an enum and not a foreign key. There is no Team table: teams are a thing the
     * model reads out of the ticket's language, and constraining it to a list this system does not
     * own would throw away the answer whenever it named a real team nobody had registered yet.
     */
    @Column(name = "suggested_team", length = 100)
    private String suggestedTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort_hint", length = 20)
    private Effort effortHint;

    /** -1.0 angry, 0.0 neutral, 1.0 delighted. Never applied to anything; read by M4's dashboard. */
    @Column(name = "sentiment_score")
    private Float sentimentScore;

    /** 0.0 to 1.0. The only field that is never null, because it gates all the others. */
    @Column(nullable = false)
    private Float confidence;

    /**
     * What the ticket failed to say — "no browser version", "no steps to reproduce".
     *
     * <p>Stored as jsonb rather than a child table. It is only ever read together with its parent
     * and never queried on its own, so a table, a foreign key and a join would buy nothing.
     * Hibernate 6 maps a {@code List<String>} onto jsonb with this one annotation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_info", columnDefinition = "jsonb")
    private List<String> missingInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    /**
     * Which model said this, for example {@code llama-3.3-70b-versatile} or {@code stub-v1}.
     *
     * <p>Not decoration. It is what tells you months later that a run of bad suggestions all came
     * from one model, and it is what makes the keyword stub visible on screen instead of passing
     * itself off as AI.
     */
    @Column(name = "model_version", nullable = false, length = 60)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
