package neobank.application.usecase.auth;

import neobank.application.dto.request.ChangePasswordRequest;
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
class ChangePasswordUseCaseTest {

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private ChangePasswordUseCase changePasswordUseCase;

    private final ChangePasswordRequest request = ChangePasswordRequest.builder()
            .currentPassword("OldPass1!")
            .newPassword("NewStrongPass1!")
            .build();

    @Test
    void changesPasswordThroughCognito() {
        changePasswordUseCase.execute(request, "access-token-123");

        verify(cognitoAdapter).changePassword("access-token-123", "OldPass1!", "NewStrongPass1!");
    }

    @Test
    void propagatesInvalidCurrentPassword() {
        doThrow(new UnauthorizedException("INVALID_PASSWORD", "Current password is incorrect"))
                .when(cognitoAdapter).changePassword("access-token-123", "OldPass1!", "NewStrongPass1!");

        assertThatThrownBy(() -> changePasswordUseCase.execute(request, "access-token-123"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
