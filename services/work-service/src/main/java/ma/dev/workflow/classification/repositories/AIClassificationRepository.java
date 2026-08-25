package ma.dev.workflow.classification.repositories;

import ma.dev.workflow.classification.models.AIClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AIClassificationRepository extends JpaRepository<AIClassification, Long> {

    /**
     * The lookup the listener does before writing. RabbitMQ delivers at least once, so a
     * classification can arrive twice; finding the existing row and updating it is what makes a
     * redelivery harmless instead of a constraint violation in the dead-letter queue.
     */
    Optional<AIClassification> findByIssueId(Long issueId);
}
