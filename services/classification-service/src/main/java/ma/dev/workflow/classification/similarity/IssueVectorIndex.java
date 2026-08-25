package ma.dev.workflow.classification.similarity;

import ma.dev.workflow.classification.classify.dto.IssueCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Remembers what every issue says, so a new one can be checked against them.
 *
 * <p>The vectors are derived data, not a record. Losing this table costs a reindex, not history —
 * which is why it lives in its own database and why replaying {@code issue.created} is enough to
 * rebuild it.
 */
@Component
public class IssueVectorIndex {

    private static final Logger log = LoggerFactory.getLogger(IssueVectorIndex.class);

    /** Metadata keys. Enough to render a match without asking work-service anything. */
    static final String ISSUE_ID = "issueId";
    static final String ISSUE_KEY = "issueKey";
    static final String TITLE = "title";
    static final String PROJECT_KEY = "projectKey";

    private final VectorStore vectorStore;
    private final double threshold;
    private final int maxResults;

    public IssueVectorIndex(VectorStore vectorStore,
                            @Value("${app.similarity.threshold}") double threshold,
                            @Value("${app.similarity.max-results}") int maxResults) {
        this.vectorStore = vectorStore;
        this.threshold = threshold;
        this.maxResults = maxResults;
    }

    /**
     * Stores or replaces the vector for one issue.
     *
     * <p>Deletes first. The store has no notion of "upsert", so without this a reindex would leave
     * two vectors for the same ticket and the duplicate panel would offer it to you twice.
     */
    public void index(IssueCreated issue) {
        remove(issue.issueId());

        Document document = new Document(textOf(issue), Map.of(
                ISSUE_ID, String.valueOf(issue.issueId()),
                ISSUE_KEY, nullSafe(issue.issueKey()),
                TITLE, nullSafe(issue.title()),
                PROJECT_KEY, nullSafe(issue.projectKey())));

        vectorStore.add(List.of(document));
        log.debug("Indexed {}", issue.issueKey());
    }

    /** Forgets an issue. Called when one is deleted, and before re-indexing it. */
    public void remove(Long issueId) {
        try {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq(ISSUE_ID, String.valueOf(issueId))
                    .build());
        } catch (RuntimeException ex) {
            // Deleting something that was never indexed is not a failure - it is the normal case
            // for an issue created before M4. Worth a line, not worth failing the message over.
            log.debug("Nothing to remove for issue {}: {}", issueId, ex.getMessage());
        }
    }

    /**
     * Forgets a whole project.
     *
     * <p>Deleting a project cascades to its issues in the database without
     * {@code IssueService.deleteById} ever running, so no {@code issue.deleted} is published for
     * any of them. Without this every one of those vectors would be orphaned and the panel would
     * go on suggesting tickets from a project that no longer exists.
     *
     * <p>Deletes by {@code projectKey}, which has no index — unlike issue id. That is deliberate:
     * projects are deleted rarely, and an index maintained on every insert to serve a rare scan is
     * the wrong trade.
     */
    public void removeProject(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return;
        }
        try {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq(PROJECT_KEY, projectKey)
                    .build());
            log.info("Forgot every vector in project {}", projectKey);
        } catch (RuntimeException ex) {
            log.debug("Nothing to remove for project {}: {}", projectKey, ex.getMessage());
        }
    }

    /**
     * The tickets most like this text, within one project.
     *
     * <p>Scoped by project because a duplicate in somebody else's project is not a duplicate. The
     * filter runs in the database rather than over the results, so a busy project cannot push
     * another project's matches out of the top five.
     */
    public List<SimilarIssue> findSimilar(String text, String projectKey, Long excludeIssueId) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(text)
                .topK(maxResults)
                .similarityThreshold(threshold);

        if (projectKey != null && !projectKey.isBlank()) {
            request.filterExpression(new FilterExpressionBuilder()
                    .eq(PROJECT_KEY, projectKey)
                    .build());
        }

        List<Document> hits = vectorStore.similaritySearch(request.build());
        if (hits == null) {
            return List.of();
        }

        return hits.stream()
                .map(SimilarIssue::from)
                .filter(hit -> hit != null)
                // An issue is not a duplicate of itself. Only matters when checking an issue that
                // is already filed; when typing a new one there is no id to exclude.
                .filter(hit -> excludeIssueId == null || !excludeIssueId.equals(hit.issueId()))
                .toList();
    }

    /**
     * What actually gets embedded.
     *
     * <p>Title and description together. The title alone is too short to carry meaning — "Login
     * broken" and "Export broken" are close in a way that says nothing — and the description alone
     * misses tickets that have none.
     */
    private String textOf(IssueCreated issue) {
        String title = nullSafe(issue.title());
        String description = nullSafe(issue.description());
        return description.isBlank() ? title : title + "\n\n" + description;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
