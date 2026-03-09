package neobank.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.*;
import neobank.application.dto.response.*;
import neobank.application.usecase.auth.*;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SignupUseCase signupUseCase;
    private final LoginUseCase loginUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final CognitoAdapter cognitoAdapter;
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Signup request received for email: {}", request.getEmail());

        SignupResponse response = signupUseCase.execute(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<VerifyEmailResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Email verification request for: {}", request.getEmail());

        VerifyEmailResponse response = verifyEmailUseCase.execute(request);

        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<ApiResponse<String>> resendCode(
            @Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Resend code request for: {}", request.getEmail());

        cognitoAdapter.resendConfirmationCode(request.getEmail());

        return ResponseEntity.ok(ApiResponse.success("Verification code sent to your email"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());

        LoginResponse response = loginUseCase.execute(request);

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");

        RefreshTokenResponse response = refreshTokenUseCase.execute(request);

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@RequestHeader("Authorization") String token,
                                                      @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Logout request for user: {}", userPrincipal.getId());

        String accessToken = token.substring(7);
        logoutUseCase.execute(accessToken, null);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password request for: {}", request.getEmail());

        forgotPasswordUseCase.execute(request);

        return ResponseEntity.ok(ApiResponse.success("Password reset code sent to your email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Reset password request for: {}", request.getEmail());

        resetPasswordUseCase.execute(request);

        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                              @RequestHeader("Authorization") String token) {
        log.info("Change password request");

        String accessToken = token.substring(7);
        changePasswordUseCase.execute(request, accessToken);

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }
}