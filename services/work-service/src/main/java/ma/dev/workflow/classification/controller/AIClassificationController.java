package ma.dev.workflow.classification.controller;

import ma.dev.workflow.classification.dto.AIClassificationDTO;
import ma.dev.workflow.classification.service.IAIClassificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nested under the issue, because a classification has no life of its own — it is a property of
 * one ticket and it dies with it.
 *
 * <p>No new gateway route is needed: {@code /issues/**} already routes to this service.
 */
@RestController
@RequestMapping("/issues/{issueId}/classification")
public class AIClassificationController {

    private final IAIClassificationService classificationService;

    public AIClassificationController(IAIClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    /**
     * The suggestion, or 204 while the classifier has not answered yet.
     *
     * <p>204 and not 404 on purpose. The chip polls this every couple of seconds, and 404 means
     * "no such URL" — a client cannot tell that apart from a typo in the path or a service that
     * was never deployed. 204 says the URL is right and the answer is not here yet, which is a
     * different thing and the only honest one while a queue is still working.
     */
    @GetMapping
    public ResponseEntity<AIClassificationDTO> findOne(@PathVariable Long issueId) {
        return classificationService.findByIssueId(issueId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The person agrees. The suggested values are written onto the issue. */
    @PostMapping("/accept")
    public AIClassificationDTO accept(@PathVariable Long issueId) {
        return classificationService.accept(issueId);
    }

    /**
     * The person disagrees. Nothing is written to the issue.
     *
     * <p>It still has to be recorded: M4's AI agreement rate is counted from these two outcomes,
     * so an override that was never logged is a disagreement the dashboard cannot see.
     */
    @PostMapping("/override")
    public AIClassificationDTO override(@PathVariable Long issueId) {
        return classificationService.override(issueId);
    }
}
