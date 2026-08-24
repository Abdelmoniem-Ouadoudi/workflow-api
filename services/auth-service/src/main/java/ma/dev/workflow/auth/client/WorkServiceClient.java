package ma.dev.workflow.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.common.exception.BusinessRuleException;
import ma.dev.workflow.auth.common.exception.ExternalServiceException;
import ma.dev.workflow.auth.token.JwtIssuer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Creates the user profile that lives in work-service.
 *
 * <p>This service stores a login. work-service stores the person. Registration therefore has to
 * write in two databases, and only one of them is ours.
 */
@Component
public class WorkServiceClient {

    /** Resolved through Eureka by the load balancer. Never a host and port. */
    private static final String WORK_SERVICE_URL = "http://work-service";

    private final RestClient restClient;
    private final JwtIssuer jwtIssuer;

    public WorkServiceClient(
            // Qualified by name, not by type: there are two RestClient.Builder beans and the
            // primary one is deliberately not load balanced. See RestClientConfig.
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
            JwtIssuer jwtIssuer) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(WORK_SERVICE_URL).build();
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Creates the profile and returns its id, which becomes {@code account.work_user_id}.
     *
     * @throws BusinessRuleException    work-service refused the data, e.g. the username or email is
     *                                  already taken there
     * @throws ExternalServiceException work-service is down, unregistered, or answered nonsense
     */
    public Long createUser(String username, String email, Role role) {
        try {
            CreatedUser created = restClient.post()
                    .uri("/users")
                    // Registration happens before the person has a token, so this call carries the
                    // service's own. work-service requires ROLE_SERVICE on POST /users, so the
                    // endpoint is not left open to make registration work.
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtIssuer.issueForService())
                    .body(new NewUser(username, email, role.name()))
                    .retrieve()
                    .onStatus(status -> status.equals(HttpStatus.UNPROCESSABLE_ENTITY),
                            (request, response) -> {
                                throw new BusinessRuleException("PROFILE_EXISTS",
                                        "A profile with this username or email already exists, "
                                                + "but it has no login attached to it.");
                            })
                    .body(CreatedUser.class);

            if (created == null || created.id() == null) {
                throw new ExternalServiceException("work-service created the profile but returned no id.");
            }
            return created.id();

        } catch (BusinessRuleException | ExternalServiceException ex) {
            throw ex;
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException is what the load balancer throws when Eureka knows no live
            // instance of work-service, which is a different failure from an HTTP error but the
            // same answer to the caller: try again later.
            throw new ExternalServiceException(
                    "Cannot reach work-service to create the profile. Registration was not completed.", ex);
        }
    }

    /** The subset of work-service's UserDTO this service sends. It ignores anything else. */
    private record NewUser(String username, String email, String role) {
    }

    /** Only the id matters here. The profile itself is work-service's business. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedUser(Long id) {
    }
}
