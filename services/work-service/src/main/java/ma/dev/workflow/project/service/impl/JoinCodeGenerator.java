package ma.dev.workflow.project.service.impl;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Makes the secret a project manager mails to somebody they want on the project.
 *
 * <p>Two decisions, both about the alphabet rather than the randomness.
 *
 * <p><strong>{@code 0 O 1 I} are not in it.</strong> This code gets read off one screen and typed
 * into another, or dictated over a phone. Allowing both O and 0 turns every wrong guess into a
 * support conversation, and the person cannot tell which of the two they got wrong.
 *
 * <p><strong>Twelve characters of a 32-symbol alphabet is about 60 bits.</strong> That is what makes
 * the lookup endpoint safe to expose: guessing a live code is not a practical attack, so the code
 * can be checked without first knowing which project it belongs to.
 */
@Component
public class JoinCodeGenerator {

    /** Crockford-style: the full alphabet minus the four characters that get confused in pairs. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 12;

    // SecureRandom, not Random. Random is seeded from the clock and its sequence can be
    // reconstructed from a couple of outputs, which for a shared secret is the whole game.
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
