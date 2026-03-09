package neobank.application.usecase.auth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.ResetPasswordRequest;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResetPasswordUseCase {

    private final CognitoAdapter cognitoAdapter;

    public void execute(ResetPasswordRequest request) {
        log.info("Resetting password for email: {}", request.getEmail());

        cognitoAdapter.confirmForgotPassword(
                request.getEmail(),
                request.getCode(),
                request.getNewPassword()
        );

        log.info("Password reset successfully for: {}", request.getEmail());
    }
}