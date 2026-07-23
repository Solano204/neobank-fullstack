package neobank.application.usecase.auth;

import neobank.application.dto.request.ResetPasswordRequest;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private ResetPasswordUseCase resetPasswordUseCase;

    private final ResetPasswordRequest request = ResetPasswordRequest.builder()
            .email("user@neobank.mx")
            .code("123456")
            .newPassword("NewStrongPass1!")
            .build();

    @Test
    void confirmsPasswordResetWithCognito() {
        resetPasswordUseCase.execute(request);

        verify(cognitoAdapter).confirmForgotPassword("user@neobank.mx", "123456", "NewStrongPass1!");
    }

    @Test
    void propagatesInvalidCodeError() {
        doThrow(new UnauthorizedException("INVALID_CODE", "Invalid reset code"))
                .when(cognitoAdapter).confirmForgotPassword("user@neobank.mx", "123456", "NewStrongPass1!");

        assertThatThrownBy(() -> resetPasswordUseCase.execute(request))
                .isInstanceOf(UnauthorizedException.class);
    }
}
