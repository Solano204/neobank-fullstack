package neobank.application.usecase.account;

import neobank.application.dto.response.AccountResponse;
import neobank.application.usecase.mapper.AccountMapper;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountDetailsUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private GetAccountDetailsUseCase getAccountDetailsUseCase;

    @Test
    void returnsMappedAccountForOwner() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).build();
        AccountResponse expected = AccountResponse.builder().id(accountId).build();

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountMapper.toResponse(account)).thenReturn(expected);

        AccountResponse response = getAccountDetailsUseCase.execute(accountId, userId);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void throwsWhenAccountNotFoundForUser() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAccountDetailsUseCase.execute(accountId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
