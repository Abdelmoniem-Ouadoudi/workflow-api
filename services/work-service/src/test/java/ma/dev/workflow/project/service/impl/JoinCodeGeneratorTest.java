package ma.dev.workflow.project.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alphabet, not the randomness.
 *
 * <p>{@code SecureRandom} is not this project's to test. What is worth pinning is the decision
 * around it: a join code is read off one screen and typed into another, or dictated down a phone,
 * so O/0 and I/1 must not both be possible. That rule lives in one string constant and would be
 * undone by a single well-meaning edit, with a failure that only ever shows up as somebody
 * insisting the code they were given does not work.
 */
class JoinCodeGeneratorTest {

    private final JoinCodeGenerator generator = new JoinCodeGenerator();

    @Test
    @DisplayName("never contains characters that get confused for each other")
    void avoidsAmbiguousCharacters() {
        // 2000 codes is 24000 characters: enough that a stray symbol in the alphabet shows up.
        IntStream.range(0, 2000).forEach(i ->
                assertThat(generator.generate()).doesNotContain("0", "O", "1", "I"));
    }

    @Test
    @DisplayName("is twelve upper-case characters")
    void hasAFixedShape() {
        assertThat(generator.generate()).hasSize(12).matches("[A-Z2-9]{12}");
    }

    /**
     * Not a uniqueness proof — the unique constraint on {@code project.join_code} is that. This
     * only catches a generator that has stopped varying at all, which is what a broken refactor
     * of the loop looks like.
     */
    @Test
    @DisplayName("does not repeat itself")
    void producesDifferentCodes() {
        Set<String> codes = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> codes.add(generator.generate()));

        assertThat(codes).hasSize(1000);
    }
}
