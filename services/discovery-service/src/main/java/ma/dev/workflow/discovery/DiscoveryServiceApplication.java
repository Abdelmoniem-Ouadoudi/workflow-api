package ma.dev.workflow.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * The registry every other service looks up and registers with.
 * Runs as one instance: clustering it is a known extension, not an oversight,
 * see docs/ARCHITECTURE-NOTES.md.
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
