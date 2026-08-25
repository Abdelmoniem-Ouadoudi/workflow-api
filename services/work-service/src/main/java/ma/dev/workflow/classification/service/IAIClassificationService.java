package ma.dev.workflow.classification.service;

import ma.dev.workflow.classification.dto.AIClassificationDTO;
import ma.dev.workflow.issue.events.IssueClassifiedEvent;

import java.util.Optional;

public interface IAIClassificationService {

    /**
     * Stores the model's answer and applies it if it is confident enough.
     *
     * <p>Called by the broker listener, never by a controller. Safe to call twice with the same
     * message: RabbitMQ delivers at least once.
     */
    void record(IssueClassifiedEvent event);

    /** Empty while the classifier has not answered yet, which the chip renders as "reading". */
    Optional<AIClassificationDTO> findByIssueId(Long issueId);

    /** The person agreed: write the suggested values onto the issue. */
    AIClassificationDTO accept(Long issueId);

    /** The person disagreed: the issue keeps its values, and the disagreement is recorded. */
    AIClassificationDTO override(Long issueId);
}
