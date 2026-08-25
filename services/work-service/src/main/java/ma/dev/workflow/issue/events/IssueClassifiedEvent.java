package ma.dev.workflow.issue.events;

import java.util.List;

/**
 * The model's answer, coming back over the broker.
 *
 * <p>Every suggested field is nullable on purpose: a model that is not sure about the priority
 * should say nothing rather than guess, and a null here means "no opinion" while a value means
 * "this one". {@code confidence} is the only field that is always present, because it is what
 * decides whether any of the rest gets applied.
 *
 * <p>The types are plain Strings, not the work-service enums. The producer is another service and
 * cannot be trusted to send a value this one knows: an unrecognised name is validated on arrival
 * and rejected there, rather than blowing up inside the JSON converter where the message would be
 * lost before anything could log which value was wrong.
 */
public record IssueClassifiedEvent(
        Long issueId,
        String suggestedType,
        String suggestedPriority,
        String suggestedTeam,
        String effortHint,
        Float sentimentScore,
        Float confidence,
        List<String> missingInfo,
        String modelVersion
) {
}
