package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.ChangePasswordRequest;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChangePasswordUseCase {

    private final CognitoAdapter cognitoAdapter;

    public void execute(ChangePasswordRequest request, String accessToken) {
        log.info("Changing password for user");

        cognitoAdapter.changePassword(
                accessToken,
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        log.info("Password changed successfully");
    }
}