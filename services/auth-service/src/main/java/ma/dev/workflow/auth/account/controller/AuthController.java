package ma.dev.workflow.auth.account.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.auth.account.dto.CurrentUser;
import ma.dev.workflow.auth.account.dto.LoginRequest;
import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.TokenResponse;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.account.service.IAuthService;
import ma.dev.workflow.auth.token.JwtIssuer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /** The interface, never the implementation. */
    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    /** 201: a login was created. The token comes back with it, so there is no second round trip. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Answered from the verified token, with no database query.
     *
     * <p>If the signature holds then the claims were written by this service and have not been
     * altered, so re-reading the row would confirm something already proven. The React app calls
     * this once on boot to turn a token kept in localStorage back into a signed-in user.
     */
    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
        // Read through Number, not straight to Long: JSON has one number type, so a claim can come
        // back as an Integer or a Long depending on its size, and casting to Long would work in
        // testing and fail on a large id.
        Number userId = jwt.getClaim(JwtIssuer.CLAIM_USER_ID);
        return new CurrentUser(
                userId.longValue(),
                jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsString(JwtIssuer.CLAIM_ROLE)));
    }
}
