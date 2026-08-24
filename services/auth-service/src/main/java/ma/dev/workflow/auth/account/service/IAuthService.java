package ma.dev.workflow.auth.account.service;

import ma.dev.workflow.auth.account.dto.LoginRequest;
import ma.dev.workflow.auth.account.dto.RegisterRequest;
import ma.dev.workflow.auth.account.dto.TokenResponse;

public interface IAuthService {

    /** Creates the profile in work-service, then the login here, then signs the person in. */
    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
