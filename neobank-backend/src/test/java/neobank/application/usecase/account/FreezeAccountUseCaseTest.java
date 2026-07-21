package neobank.application.usecase.account;

import neobank.application.dto.request.FreezeAccountRequest;
import neobank.domain.entity.Account;
import neobank.domain.enums.AccountStatus;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.BusinessException;
import neobank.infrastructure.exception.ResourceNotFoundException;
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
class FreezeAccountUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private FreezeAccountUseCase freezeAccountUseCase;

    private UUID accountId;
    private UUID userId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        userId = UUID.randomUUID();
        account = Account.builder()
                .id(accountId)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void freezesActiveAccount() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        freezeAccountUseCase.execute(accountId, userId, new FreezeAccountRequest("suspicious activity"));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(accountRepository).save(account);
    }

    @Test
    void throwsWhenAccountNotFound() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> freezeAccountUseCase.execute(accountId, userId, new FreezeAccountRequest("reason")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void throwsWhenAlreadyFrozen() {
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> freezeAccountUseCase.execute(accountId, userId, new FreezeAccountRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account is already frozen");

        verify(accountRepository, never()).save(any());
    }
}
