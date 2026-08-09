package ma.dev.workflow.issue.models.enums;

public enum Priority {

    CRITICAL(0),
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    /**
     * Sort order, most urgent first.
     * The column is a varchar because of @Enumerated(STRING), so "ORDER BY priority" in SQL
     * would sort alphabetically (MEDIUM, LOW, HIGH, CRITICAL). Sorting uses this instead.
     * Declaring it explicitly also means reordering the constants cannot silently change it.
     */
    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
