package neobank.application.usecase.auth;

import neobank.application.dto.request.VerifyEmailRequest;
import neobank.application.dto.response.VerifyEmailResponse;
import neobank.domain.entity.User;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyEmailUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private VerifyEmailUseCase verifyEmailUseCase;

    private final VerifyEmailRequest request = VerifyEmailRequest.builder()
            .email("user@neobank.mx")
            .code("123456")
            .build();

    @Test
    void confirmsEmailAndReturnsUserSummary() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@neobank.mx")
                .fullName("Ada Lovelace")
                .kycStatus(KycStatus.PENDING)
                .build();
        when(userRepository.findByEmail("user@neobank.mx")).thenReturn(Optional.of(user));

        VerifyEmailResponse response = verifyEmailUseCase.execute(request);

        verify(cognitoAdapter).confirmSignUp("user@neobank.mx", "123456");
        assertThat(response.getUser().getEmail()).isEqualTo("user@neobank.mx");
    }

    @Test
    void propagatesInvalidCode() {
        doThrow(new UnauthorizedException("INVALID_CODE", "Invalid verification code"))
                .when(cognitoAdapter).confirmSignUp("user@neobank.mx", "123456");

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void throwsWhenUserRecordMissingAfterConfirmation() {
        when(userRepository.findByEmail("user@neobank.mx")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyEmailUseCase.execute(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
