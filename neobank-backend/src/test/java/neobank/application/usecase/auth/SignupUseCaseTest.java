package neobank.application.usecase.auth;

import neobank.application.dto.request.SignupRequest;
import neobank.application.dto.response.SignupResponse;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private SignupUseCase signupUseCase;

    private SignupRequest request;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        request = SignupRequest.builder()
                .email("new.user@neobank.mx")
                .password("StrongPass1!")
                .fullName("Ada Lovelace")
                .phone("+525512345678")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .curp("LOAA900101MDFXXX01")
                .build();
    }

    @Test
    void createsUserAccountAndSettingsOnSuccessfulSignup() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(cognitoAdapter.signUp(request.getEmail(), request.getPassword(), request.getFullName()))
                .thenReturn("cognito-sub-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        SignupResponse response = signupUseCase.execute(request);

        assertThat(response.getStatus()).isEqualTo("VERIFICATION_PENDING");
        assertThat(response.getAccountNumber()).hasSize(18);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCognitoUserId()).isEqualTo("cognito-sub-123");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(request.getEmail());

        verify(userSettingsRepository).save(any());
    }

    @Test
    void throwsWhenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> signupUseCase.execute(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This email is already registered");

        verifyNoInteractions(cognitoAdapter);
        verify(userRepository, never()).save(any());
    }
}
