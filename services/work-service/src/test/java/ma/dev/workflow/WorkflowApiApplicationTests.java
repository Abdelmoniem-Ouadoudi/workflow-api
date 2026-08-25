package ma.dev.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the whole application, which is the only way to catch a broken bean wiring — a missing
 * qualifier, two candidates for one type, a property that nothing supplies.
 *
 * <p>Tagged, and excluded from {@code mvn test} by default. It needs Postgres, RabbitMQ and Eureka
 * actually running, so on a clean checkout it does not fail because something is wrong with the
 * code — it fails because nothing is up. A suite that cannot be trusted to mean what it says is a
 * suite people stop running.
 *
 * <p>Everything else in {@code src/test} is a plain unit test with no infrastructure at all, which
 * is what makes {@code mvn test} usable as a fast check.
 *
 * <pre>
 *   mvn test               the unit tests, no infrastructure, seconds
 *   mvn test -Pall-tests   everything, with the stack up first
 * </pre>
 */
@Tag("integration")
@SpringBootTest
class WorkflowApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
