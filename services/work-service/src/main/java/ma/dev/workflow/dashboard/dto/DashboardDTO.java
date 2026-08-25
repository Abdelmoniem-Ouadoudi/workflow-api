package ma.dev.workflow.dashboard.dto;

import java.util.List;

/**
 * Everything the dashboard screen needs, in one response.
 *
 * <p>One endpoint rather than six. Six would mean six round trips, six loading states, and a screen
 * that can render half-populated while somebody watches — and the numbers would be from six
 * slightly different moments, which is worse than slow.
 */
public record DashboardDTO(

        long totalIssues,

        /** How many have been read by the AI at all. The rest predate M3 or are still in flight. */
        long classifiedIssues,

        /** Suggestions nobody has accepted or rejected yet. The queue of human work. */
        long awaitingReview,

        /**
         * How often the AI was right, as a percentage, or null when nobody has judged one yet.
         *
         * <p>{@code (AUTO_APPLIED + CONFIRMED) / (AUTO_APPLIED + CONFIRMED + OVERRIDDEN)}.
         * PENDING is excluded on purpose: a suggestion nobody has looked at is not a disagreement,
         * and counting it as one would make this number fall every time somebody files a ticket —
         * measuring activity rather than accuracy.
         *
         * <p>Null rather than zero when there is nothing to measure. Zero would read as "the AI is
         * always wrong", which is a very different claim from "nobody has checked".
         */
        Double aiAgreementRate,

        /** The three that come from the issue itself. */
        List<CountByLabel> byType,
        List<CountByLabel> byPriority,
        List<CountByLabel> byStatus,

        /**
         * The two that only exist because of the AI layer. `suggested_team` and `effort_hint` are
         * never written onto an issue — there are no such fields — so this is the only place they
         * are ever read.
         */
        List<CountByLabel> byTeam,
        List<CountByLabel> byEffort
) {
}
