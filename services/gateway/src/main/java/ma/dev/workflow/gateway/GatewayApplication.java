package ma.dev.workflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single door into the system. The React app knows this address and no other.
 *
 * <p>It does four things: it maps a URL to a service name, it asks Eureka where that service
 * actually is, it refuses a request with no valid token before it costs anyone anything, and it
 * stamps every request with a correlation id so one user action can be followed across every log.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
