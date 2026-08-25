package ma.dev.workflow.classification.similarity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The one synchronous thing this service does.
 *
 * <p>Everything else here is driven by a queue, because nobody is waiting for it. Here somebody is:
 * the answer is worthless once they have pressed submit, so this is the one call where a queue
 * would be the wrong tool.
 *
 * <p>Reached through the gateway like any other route, and it needs a token — it returns the titles
 * of other people's tickets.
 */
@RestController
@Validated
public class SimilarityController {

    private final IssueVectorIndex index;

    public SimilarityController(IssueVectorIndex index) {
        this.index = index;
    }

    /**
     * Tickets that already say roughly this.
     *
     * @param text       what the person has typed so far
     * @param projectKey scope. A duplicate in another project is not a duplicate
     * @param excludeIssueId skip this one, when checking an issue that already exists
     */
    @GetMapping("/similar")
    public List<SimilarIssue> findSimilar(
            // 10 characters is roughly where a fragment starts carrying meaning. Below it every
            // ticket looks like every other ticket, and the panel would flicker on every keystroke.
            @RequestParam @NotBlank @Size(min = 10, max = 2000) String text,
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) Long excludeIssueId) {

        return index.findSimilar(text, projectKey, excludeIssueId);
    }
}
