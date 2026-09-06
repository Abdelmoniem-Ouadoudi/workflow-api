package ma.dev.workflow.auth.account.service;

import ma.dev.workflow.auth.account.dto.LoginRequest;
import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.RegistrationReceipt;
import ma.dev.workflow.auth.account.dto.TokenResponse;

public interface IAuthService {

    /**
     * Creates the profile in work-service, then the login here, PENDING an administrator.
     *
     * <p>It does not sign the person in, because it no longer lets them in.
     */
    RegistrationReceipt register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
