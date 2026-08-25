package ma.dev.workflow.classification.similarity;

import org.springframework.ai.document.Document;

/**
 * One possible duplicate, as the panel renders it.
 *
 * <p>Everything here comes out of the vector's own metadata, so answering a search costs one query
 * and no call to work-service. The cost of that is a stale title if somebody renames a ticket
 * without it being reindexed — a trade worth making for a panel that has to answer while a person
 * is still typing. Recorded in docs/BACKLOG.md.
 */
public record SimilarIssue(
        Long issueId,
        String issueKey,
        String title,
        String projectKey,
        /**
         * 0 to 1, where 1 is identical. Shown to the reader rather than hidden: "84% alike" lets
         * somebody judge a borderline match for themselves, where a bare list asks them to trust it.
         */
        Double score
) {

    /** Null when the metadata is unreadable — an old row, or one written by a different version. */
    static SimilarIssue from(Document document) {
        Object rawId = document.getMetadata().get(IssueVectorIndex.ISSUE_ID);
        if (rawId == null) {
            return null;
        }

        try {
            return new SimilarIssue(
                    Long.parseLong(rawId.toString()),
                    asString(document, IssueVectorIndex.ISSUE_KEY),
                    asString(document, IssueVectorIndex.TITLE),
                    asString(document, IssueVectorIndex.PROJECT_KEY),
                    document.getScore());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
