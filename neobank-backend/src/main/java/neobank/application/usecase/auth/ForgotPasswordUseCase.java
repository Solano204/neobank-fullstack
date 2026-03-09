package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.ForgotPasswordRequest;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordUseCase {

    private final CognitoAdapter cognitoAdapter;

    public void execute(ForgotPasswordRequest request) {
        log.info("Password reset requested for email: {}", request.getEmail());

        cognitoAdapter.forgotPassword(request.getEmail());

        log.info("Password reset code sent to: {}", request.getEmail());
    }
}