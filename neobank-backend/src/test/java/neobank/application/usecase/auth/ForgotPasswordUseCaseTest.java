package neobank.application.usecase.auth;

import neobank.application.dto.request.ForgotPasswordRequest;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private ForgotPasswordUseCase forgotPasswordUseCase;

    @Test
    void triggersCognitoPasswordResetForEmail() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("user@neobank.mx").build();

        forgotPasswordUseCase.execute(request);

        verify(cognitoAdapter).forgotPassword("user@neobank.mx");
    }
}
