package ma.dev.workflow.project.dto;

/**
 * What a join code buys you before anybody has approved anything: enough to be sure you are asking
 * about the right project.
 *
 * <p>Three fields, and no more. No description, no member count, no issue counts — somebody holding
 * a code they were forwarded by mistake should learn that the project exists and what it is called,
 * and nothing else. That is the least that still lets an honest person check they have not pasted
 * the wrong code.
 */
public record ProjectLookupDTO(Long id, String key, String name) {
}
