package ma.dev.workflow.classification.similarity;

import ma.dev.workflow.classification.classify.dto.IssueCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What gets embedded, and what comes back.
 *
 * <p>The interesting decisions here are quiet ones: which text is turned into a vector, what
 * metadata travels with it, and the delete-before-insert that stops a reindex leaving two copies
 * of the same ticket.
 */
class IssueVectorIndexTest {

    private VectorStore vectorStore;
    private IssueVectorIndex index;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        index = new IssueVectorIndex(vectorStore, 0.45, 5);
    }

    /**
     * The title alone is too short to carry meaning — "Login broken" and "Export broken" sit close
     * together in a way that says nothing useful.
     */
    @Test
    @DisplayName("the title and the description are embedded together")
    void embedsTitleAndDescription() {
        index.index(issue("Login fails", "Users see a 500 on submit"));

        assertThat(indexed().getText())
                .contains("Login fails")
                .contains("Users see a 500 on submit");
    }

    @Test
    @DisplayName("a ticket with no description is still embedded, on its title alone")
    void embedsATitleOnlyTicket() {
        index.index(issue("Login fails", null));

        assertThat(indexed().getText().trim()).isEqualTo("Login fails");
    }

    /**
     * Everything needed to render a match, so answering a search never has to call work-service.
     */
    @Test
    @DisplayName("enough metadata travels with the vector to show a result without a second query")
    void carriesTheMetadataAResultNeeds() {
        index.index(new IssueCreated(42L, "WORK-7", "Login fails", "detail", "WORK"));

        assertThat(indexed().getMetadata())
                .containsEntry("issueId", "42")
                .containsEntry("issueKey", "WORK-7")
                .containsEntry("title", "Login fails")
                .containsEntry("projectKey", "WORK");
    }

    /**
     * The store has no upsert. Without the delete, running a reindex twice would leave two vectors
     * per ticket and the duplicate panel would offer the same issue twice.
     */
    @Test
    @DisplayName("indexing removes any existing vector first, so a reindex cannot duplicate")
    void deletesBeforeInserting() {
        index.index(issue("Login fails", "detail"));

        var order = org.mockito.Mockito.inOrder(vectorStore);
        order.verify(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
        order.verify(vectorStore).add(any());
    }

    /**
     * The normal case for an issue created before M4. Failing the message over it would send a
     * perfectly good ticket to the dead-letter queue.
     */
    @Test
    @DisplayName("removing a vector that was never there is not a failure")
    void toleratesRemovingSomethingThatIsNotIndexed() {
        org.mockito.Mockito.doThrow(new RuntimeException("nothing matched"))
                .when(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));

        index.remove(99L);  // must not throw
    }

    @Test
    @DisplayName("a project with no key is ignored rather than deleting everything")
    void refusesToDeleteAProjectWithNoKey() {
        index.removeProject(null);
        index.removeProject("  ");

        verify(vectorStore, times(0))
                .delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
    }

    @Test
    @DisplayName("an issue is never offered as a duplicate of itself")
    void excludesTheIssueBeingChecked() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(match(5L, "WORK-5"), match(6L, "WORK-6")));

        List<SimilarIssue> found = index.findSimilar("some text", "WORK", 5L);

        assertThat(found).extracting(SimilarIssue::issueId).containsExactly(6L);
    }

    @Test
    @DisplayName("a vector with unreadable metadata is skipped, not crashed on")
    void skipsAnUnreadableRow() {
        Document broken = new Document("text", java.util.Map.of("issueKey", "WORK-9"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(broken, match(6L, "WORK-6")));

        assertThat(index.findSimilar("some text", "WORK", null))
                .extracting(SimilarIssue::issueId).containsExactly(6L);
    }

    @Test
    @DisplayName("a search that returns nothing is an empty list, not a null")
    void handlesNoResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        assertThat(index.findSimilar("some text", "WORK", null)).isEmpty();
    }

    private Document indexed() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue().getFirst();
    }

    private Document match(long issueId, String issueKey) {
        return new Document("text", java.util.Map.of(
                "issueId", String.valueOf(issueId),
                "issueKey", issueKey,
                "title", "A title",
                "projectKey", "WORK"));
    }

    private IssueCreated issue(String title, String description) {
        return new IssueCreated(1L, "TEST-1", title, description, "TEST");
    }
}
