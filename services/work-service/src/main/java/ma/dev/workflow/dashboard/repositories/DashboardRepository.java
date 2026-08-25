package ma.dev.workflow.dashboard.repositories;

import ma.dev.workflow.dashboard.dto.CountByLabel;
import ma.dev.workflow.issue.models.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * The dashboard's read side.
 *
 * <p>Its own repository rather than more methods on {@code IssueRepository}, because these are
 * aggregates for one screen and not part of how issues are worked with. Bound to {@code Issue} only
 * because Spring Data needs an entity to hang a repository on — nothing here loads one.
 *
 * <p>Every query counts in the database and returns the counts. The alternative, loading the issues
 * and grouping in Java, moves every row across the wire to produce five numbers.
 */
public interface DashboardRepository extends JpaRepository<Issue, Long> {

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.type, count(i))
            from Issue i
            group by i.type
            order by count(i) desc
            """)
    List<CountByLabel> countByType();

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.priority, count(i))
            from Issue i
            group by i.priority
            order by count(i) desc
            """)
    List<CountByLabel> countByPriority();

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.status, count(i))
            from Issue i
            group by i.status
            order by count(i) desc
            """)
    List<CountByLabel> countByStatus();

    /**
     * Team load, from what the AI read out of each ticket.
     *
     * <p>The nearest thing this system has to a component or an owning team: `Issue` has no such
     * field and the class diagram gives it none. Rows with no team are grouped as "Not set" by the
     * DTO rather than dropped — a model that declines to guess is information too.
     */
    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(c.suggestedTeam, count(c))
            from AIClassification c
            group by c.suggestedTeam
            order by count(c) desc
            """)
    List<CountByLabel> countByTeam();

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(c.effortHint, count(c))
            from AIClassification c
            group by c.effortHint
            order by count(c) desc
            """)
    List<CountByLabel> countByEffort();

    /**
     * How many classifications sit in each review outcome.
     *
     * <p>The agreement rate is worked out from this in the service rather than in SQL. Two reasons:
     * the arithmetic is a rule about what counts as agreement, which belongs where a person reads
     * it, and the raw counts are needed anyway for "awaiting review".
     */
    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(c.reviewStatus, count(c))
            from AIClassification c
            group by c.reviewStatus
            """)
    List<CountByLabel> countByReviewStatus();

    @Query("select count(c) from AIClassification c")
    long countClassifications();
}
