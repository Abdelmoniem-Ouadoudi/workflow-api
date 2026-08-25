package ma.dev.workflow.classification.deadletter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two buttons that make the dead-letter queue useful.
 *
 * <p>Both require an ADMIN token, checked by this service the same way work-service checks one:
 * replaying is an operational action with real effects, and it is not something a developer
 * account should be able to do by guessing a URL.
 */
@RestController
@RequestMapping("/admin/classification")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    public DeadLetterController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    /** How many classification requests are parked. Zero is the healthy answer. */
    @GetMapping("/dead-letters")
    public DeadLetterCount count() {
        return new DeadLetterCount(deadLetterService.count());
    }

    /** Puts them back on the work queue. Call it again until it reports zero. */
    @PostMapping("/replay")
    public ReplayResult replay() {
        int moved = deadLetterService.replay();
        return new ReplayResult(moved, deadLetterService.count());
    }

    public record DeadLetterCount(int deadLetters) {
    }

    /** {@code remaining} is what tells the caller whether one more call is needed. */
    public record ReplayResult(int replayed, int remaining) {
    }
}
