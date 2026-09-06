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

import java.util.List;

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
    public Long createUser(String username, String email, Role role, boolean active) {
        try {
            CreatedUser created = restClient.post()
                    .uri("/users")
                    // Registration happens before the person has a token, so this call carries the
                    // service's own. work-service requires ROLE_SERVICE on POST /users, so the
                    // endpoint is not left open to make registration work.
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtIssuer.issueForService())
                    .body(new NewUser(username, email, role.name(), active))
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

    /**
     * Every profile, so the administrator's screen can show an email next to each account.
     *
     * <p>One call, not one per row. {@code account} has no email by design — it belongs to
     * {@code app_user}, which work-service owns — so the admin list has to be assembled from both
     * sides. It is assembled <em>here</em>, and that direction is the whole argument: auth-service
     * already calls work-service. Building the same list in work-service would make work-service
     * call auth-service, and two services that call each other are a cycle that has to be broken
     * before either can be deployed alone.
     */
    public List<WorkUser> listUsers() {
        try {
            WorkUser[] users = restClient.get()
                    .uri("/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtIssuer.issueForService())
                    .retrieve()
                    .body(WorkUser[].class);
            return users == null ? List.of() : List.of(users);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ExternalServiceException(
                    "Cannot reach work-service to read the user list.", ex);
        }
    }

    /**
     * Mirrors a role change into the profile.
     *
     * <p>The role lives in two places and this is the only thing that writes the second one. Until
     * M5 {@code PUT /users/{id}} could change {@code app_user.role} directly while the token kept
     * being minted from {@code account.role}, so a promoted person carried their old role forever
     * and nothing ever said so.
     */
    public void updateRole(Long workUserId, Role role) {
        put("/users/" + workUserId + "/role", new RoleUpdate(role.name()),
                "Cannot reach work-service to update the profile's role.");
    }

    /** Mirrors approval or deactivation, so an unapproved person is not offered as an assignee. */
    public void updateActive(Long workUserId, boolean active) {
        put("/users/" + workUserId + "/active", new ActiveUpdate(active),
                "Cannot reach work-service to update the profile's status.");
    }

    private void put(String uri, Object body, String failureMessage) {
        try {
            restClient.put()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtIssuer.issueForService())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | IllegalStateException ex) {
            throw new ExternalServiceException(failureMessage, ex);
        }
    }

    /** The subset of work-service's UserDTO this service sends. It ignores anything else. */
    private record NewUser(String username, String email, String role, boolean active) {
    }

    /** Only the id matters here. The profile itself is work-service's business. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedUser(Long id) {
    }

    /** What the admin screen needs from a profile. Public: the admin service maps it onto a DTO. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkUser(Long id, String username, String email, Boolean active) {
    }

    private record RoleUpdate(String role) {
    }

    private record ActiveUpdate(boolean active) {
    }
}
