package ma.dev.workflow.dashboard.repositories;

import ma.dev.workflow.dashboard.dto.CountByLabel;
import ma.dev.workflow.issue.models.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
 *
 * <p><strong>Since M5 every query takes the projects the caller may see.</strong> Before that they
 * were all a {@code group by} with no {@code where}, so the numbers on the screen were the whole
 * system's — which BACKLOG item 36 recorded as a gap and left open, because "which project is this
 * screen for" had no answer yet. Membership answers it: the screen is for the projects you are on.
 * An administrator passes every project id and gets the system-wide view back.
 *
 * <p>The caller must never pass an empty list — JPQL {@code in ()} is not valid SQL and the
 * rendering of an empty collection has changed between Hibernate versions. {@code DashboardService}
 * returns an empty dashboard without asking.
 */
public interface DashboardRepository extends JpaRepository<Issue, Long> {

    @Query("select count(i) from Issue i where i.project.id in :projectIds")
    long countIssues(@Param("projectIds") List<Long> projectIds);

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.type, count(i))
            from Issue i
            where i.project.id in :projectIds
            group by i.type
            order by count(i) desc
            """)
    List<CountByLabel> countByType(@Param("projectIds") List<Long> projectIds);

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.priority, count(i))
            from Issue i
            where i.project.id in :projectIds
            group by i.priority
            order by count(i) desc
            """)
    List<CountByLabel> countByPriority(@Param("projectIds") List<Long> projectIds);

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(i.status, count(i))
            from Issue i
            where i.project.id in :projectIds
            group by i.status
            order by count(i) desc
            """)
    List<CountByLabel> countByStatus(@Param("projectIds") List<Long> projectIds);

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
            where c.issue.project.id in :projectIds
            group by c.suggestedTeam
            order by count(c) desc
            """)
    List<CountByLabel> countByTeam(@Param("projectIds") List<Long> projectIds);

    @Query("""
            select new ma.dev.workflow.dashboard.dto.CountByLabel(c.effortHint, count(c))
            from AIClassification c
            where c.issue.project.id in :projectIds
            group by c.effortHint
            order by count(c) desc
            """)
    List<CountByLabel> countByEffort(@Param("projectIds") List<Long> projectIds);

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
            where c.issue.project.id in :projectIds
            group by c.reviewStatus
            """)
    List<CountByLabel> countByReviewStatus(@Param("projectIds") List<Long> projectIds);

    @Query("select count(c) from AIClassification c where c.issue.project.id in :projectIds")
    long countClassifications(@Param("projectIds") List<Long> projectIds);
}
