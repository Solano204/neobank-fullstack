package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.*;
import neobank.application.dto.response.LoginResponse;
import neobank.application.dto.response.RefreshTokenResponse;
import neobank.application.dto.response.SignupResponse;
import neobank.application.dto.response.VerifyEmailResponse;
import neobank.application.usecase.auth.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SignupUseCase signupUseCase;
    private final LoginUseCase loginUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    public SignupResponse signup(SignupRequest request) {
        return signupUseCase.execute(request);
    }

    public LoginResponse login(LoginRequest request) {
        return loginUseCase.execute(request);
    }

    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        return verifyEmailUseCase.execute(request);
    }

    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        return refreshTokenUseCase.execute(request);
    }

    public void logout(String accessToken) {
        logoutUseCase.execute(accessToken, null);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        forgotPasswordUseCase.execute(request);
    }

    public void resetPassword(ResetPasswordRequest request) {
        resetPasswordUseCase.execute(request);
    }

    public void changePassword(ChangePasswordRequest request, String accessToken) {
        changePasswordUseCase.execute(request, accessToken);
    }
}