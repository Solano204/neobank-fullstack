package neobank.application.usecase.auth;

import neobank.application.dto.request.LoginRequest;
import neobank.application.dto.response.LoginResponse;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private LoginUseCase loginUseCase;

    private final LoginRequest request = LoginRequest.builder()
            .email("user@neobank.mx")
            .password("StrongPass1!")
            .build();

    @Test
    void returnsTokensAndUserOnSuccessfulLogin() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email(request.getEmail())
                .fullName("Ada Lovelace")
                .phone("+525512345678")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        Account account = Account.builder().accountNumber("123456789012345678").build();

        when(cognitoAdapter.login(request.getEmail(), request.getPassword()))
                .thenReturn(Map.of("accessToken", "access-123", "refreshToken", "refresh-123"));
        when(cognitoAdapter.getUserIdFromToken("access-123")).thenReturn("cognito-sub-123");
        when(userRepository.findByCognitoUserId("cognito-sub-123")).thenReturn(Optional.of(user));
        when(accountRepository.findByUser(user)).thenReturn(List.of(account));

        LoginResponse response = loginUseCase.execute(request);

        assertThat(response.getAccessToken()).isEqualTo("access-123");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-123");
        assertThat(response.getUser().getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getUser().getAccountNumber()).isEqualTo("123456789012345678");
    }

    @Test
    void returnsNullAccountNumberWhenUserHasNoAccounts() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email(request.getEmail()).build();

        when(cognitoAdapter.login(request.getEmail(), request.getPassword()))
                .thenReturn(Map.of("accessToken", "access-123", "refreshToken", "refresh-123"));
        when(cognitoAdapter.getUserIdFromToken("access-123")).thenReturn("cognito-sub-123");
        when(userRepository.findByCognitoUserId("cognito-sub-123")).thenReturn(Optional.of(user));
        when(accountRepository.findByUser(user)).thenReturn(List.of());

        LoginResponse response = loginUseCase.execute(request);

        assertThat(response.getUser().getAccountNumber()).isNull();
    }

    @Test
    void propagatesUnauthorizedForInvalidCredentials() {
        when(cognitoAdapter.login(request.getEmail(), request.getPassword()))
                .thenThrow(new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void throwsWhenCognitoUserHasNoLocalRecord() {
        when(cognitoAdapter.login(request.getEmail(), request.getPassword()))
                .thenReturn(Map.of("accessToken", "access-123", "refreshToken", "refresh-123"));
        when(cognitoAdapter.getUserIdFromToken("access-123")).thenReturn("cognito-sub-123");
        when(userRepository.findByCognitoUserId("cognito-sub-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
