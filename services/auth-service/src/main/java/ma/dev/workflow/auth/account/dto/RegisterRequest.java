package ma.dev.workflow.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The email is validated here but never stored here: it is forwarded to work-service, which owns
 * the profile. Keeping a copy would mean two rows to change when someone updates their address.
 *
 * <p><strong>There is no role field, and its absence is the point.</strong> Until M5 there was one,
 * on an endpoint open to anybody, which meant the sentence "anyone on the internet can make
 * themselves an administrator of this system" was literally true. Everyone now registers as a
 * PENDING DEVELOPER and an administrator decides what they are — which is the whole reason the
 * approval queue exists.
 */
public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
) {
}
