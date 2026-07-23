package neobank.application.usecase.account;

import neobank.application.dto.response.AccountResponse;
import neobank.application.usecase.mapper.AccountMapper;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountsUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private GetAccountsUseCase getAccountsUseCase;

    @Test
    void returnsAllAccountsMappedForUser() {
        UUID userId = UUID.randomUUID();
        Account checking = Account.builder().id(UUID.randomUUID()).build();
        Account savings = Account.builder().id(UUID.randomUUID()).build();
        AccountResponse checkingResponse = AccountResponse.builder().id(checking.getId()).build();
        AccountResponse savingsResponse = AccountResponse.builder().id(savings.getId()).build();

        when(accountRepository.findByUserId(userId)).thenReturn(List.of(checking, savings));
        when(accountMapper.toResponse(checking)).thenReturn(checkingResponse);
        when(accountMapper.toResponse(savings)).thenReturn(savingsResponse);

        List<AccountResponse> responses = getAccountsUseCase.execute(userId);

        assertThat(responses).containsExactly(checkingResponse, savingsResponse);
    }

    @Test
    void returnsEmptyListWhenUserHasNoAccounts() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(List.of());

        List<AccountResponse> responses = getAccountsUseCase.execute(userId);

        assertThat(responses).isEmpty();
    }
}
