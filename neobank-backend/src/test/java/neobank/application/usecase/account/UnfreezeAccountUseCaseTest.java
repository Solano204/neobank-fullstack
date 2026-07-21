package neobank.application.usecase.account;

import neobank.application.dto.request.UnfreezeAccountRequest;
import neobank.domain.entity.Account;
import neobank.domain.enums.AccountStatus;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.BusinessException;
import neobank.infrastructure.exception.ResourceNotFoundException;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnfreezeAccountUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private UnfreezeAccountUseCase unfreezeAccountUseCase;

    private UUID accountId;
    private UUID userId;
    private Account account;
    private final String email = "user@neobank.mx";

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        userId = UUID.randomUUID();
        account = Account.builder()
                .id(accountId)
                .status(AccountStatus.FROZEN)
                .build();
    }

    @Test
    void unfreezesFrozenAccountAfterPasswordVerification() {
        UnfreezeAccountRequest request = new UnfreezeAccountRequest("correct-password");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        unfreezeAccountUseCase.execute(accountId, userId, request, email);

        verify(cognitoAdapter).verifyPassword(email, "correct-password");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).save(account);
    }

    @Test
    void propagatesUnauthorizedWhenPasswordIsWrong() {
        UnfreezeAccountRequest request = new UnfreezeAccountRequest("wrong-password");
        doThrow(new UnauthorizedException("INVALID_PASSWORD", "Password is incorrect"))
                .when(cognitoAdapter).verifyPassword(email, "wrong-password");

        assertThatThrownBy(() -> unfreezeAccountUseCase.execute(accountId, userId, request, email))
                .isInstanceOf(UnauthorizedException.class);

        verify(accountRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void throwsWhenAccountNotFound() {
        UnfreezeAccountRequest request = new UnfreezeAccountRequest("correct-password");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> unfreezeAccountUseCase.execute(accountId, userId, request, email))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenAccountIsNotFrozen() {
        account.setStatus(AccountStatus.ACTIVE);
        UnfreezeAccountRequest request = new UnfreezeAccountRequest("correct-password");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> unfreezeAccountUseCase.execute(accountId, userId, request, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account is not frozen");

        verify(accountRepository, never()).save(any());
    }
}
