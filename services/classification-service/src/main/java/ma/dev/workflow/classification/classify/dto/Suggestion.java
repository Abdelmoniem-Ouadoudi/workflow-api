package ma.dev.workflow.classification.classify.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * What the model is asked to produce.
 *
 * <p>This record is the prompt's other half. Spring AI turns it into a JSON schema, puts that
 * schema in the request, and maps the reply back onto it — so the field names and the descriptions
 * below are not documentation, they are the instructions the model actually reads. Renaming a
 * field here changes what the model is asked for.
 *
 * <p>Every field is nullable. A model that is not sure should return null rather than guess, and
 * saying so in the descriptions is what makes that happen instead of a confident invention.
 */
@JsonClassDescription("A triage assessment of one work ticket.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Suggestion(

        @JsonPropertyDescription(
                "One of BUG, FEATURE, SUPPORT, TASK. Null if the ticket does not say enough to tell.")
        String type,

        @JsonPropertyDescription(
                "One of LOW, MEDIUM, HIGH, CRITICAL. Judge by user impact and urgency, "
                        + "not by how strongly the ticket is worded. Null if unclear.")
        String priority,

        @JsonPropertyDescription(
                "The team most likely to own this, such as Backend, Frontend, Infrastructure "
                        + "or Data. Null if the ticket gives no hint.")
        String team,

        @JsonPropertyDescription(
                "One of SMALL, MEDIUM, LARGE. How much work this looks like. Null if unclear.")
        String effort,

        @JsonPropertyDescription(
                "How the reporter sounds, from -1.0 for angry through 0.0 for neutral "
                        + "to 1.0 for pleased.")
        Float sentiment,

        @JsonPropertyDescription(
                "How sure you are of this assessment overall, from 0.0 to 1.0. "
                        + "Be honest: a low number is more useful than a wrong high one.")
        Float confidence,

        @JsonPropertyDescription(
                "Facts a developer would need that this ticket does not give, each as a short "
                        + "phrase, for example 'no steps to reproduce' or 'no browser version'. "
                        + "An empty list if the ticket is complete.")
        List<String> missingInfo
) {
}
