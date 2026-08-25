package ma.dev.workflow.classification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reads a ticket and says what it thinks it is.
 *
 * <p>A queue worker, not an API. It consumes {@code issue.created}, asks a model, and publishes
 * {@code issue.classified}. It has no database: the answer is stored by work-service, next to the
 * issue it describes, which is what leaves this service free of state and therefore free to be run
 * more than once.
 */
@SpringBootApplication
public class ClassificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassificationServiceApplication.class, args);
    }
}
