package neobank.application.usecase.account;

import neobank.application.dto.response.AccountBalanceResponse;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private GetAccountBalanceUseCase getAccountBalanceUseCase;

    @Test
    void returnsBalanceForOwnedAccount() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .accountNumber("123456789012345678")
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("950.00"))
                .currency("MXN")
                .build();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        AccountBalanceResponse response = getAccountBalanceUseCase.execute(accountId, userId);

        assertThat(response.getAccountNumber()).isEqualTo("123456789012345678");
        assertThat(response.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("950.00");
        assertThat(response.getCurrency()).isEqualTo("MXN");
    }

    @Test
    void throwsWhenAccountDoesNotBelongToUser() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAccountBalanceUseCase.execute(accountId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
