package ma.dev.workflow.dashboard.dto;

/**
 * One bar on a chart: what it is, and how many.
 *
 * <p>Deliberately one shape for every distribution on the dashboard, rather than a type per
 * metric. The frontend then draws bars once and reuses that for type, priority, status, team and
 * effort — five charts from one component, and adding a sixth costs nothing.
 */
public record CountByLabel(
        String label,
        long count
) {

    /**
     * Used by the JPQL constructor projections, which hand back an enum or a String depending on
     * the column. {@code null} becomes a readable label rather than an empty bar: the AI genuinely
     * declines to guess a team sometimes, and "not set" is the honest word for that.
     */
    public CountByLabel(Object label, Long count) {
        this(label == null ? "Not set" : label.toString(), count == null ? 0L : count);
    }
}
