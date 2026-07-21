package neobank.application.usecase.support;

import neobank.application.dto.response.AccountResponse;
import neobank.application.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSupportUseCaseTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private ChatSupportUseCase chatSupportUseCase;

    @Test
    void answersBalanceQuestionsWithTheRealTotal() {
        UUID userId = UUID.randomUUID();
        when(accountService.getAccounts(userId)).thenReturn(List.of(
                AccountResponse.builder().availableBalance(new BigDecimal("1000.00")).build(),
                AccountResponse.builder().availableBalance(new BigDecimal("250.50")).build()
        ));

        ChatSupportUseCase.Result result = chatSupportUseCase.execute(userId, "what's my balance?");

        assertThat(result.intent()).isEqualTo("CheckBalance");
        assertThat(result.message()).contains("1250.50");
    }

    @Test
    void reportsNoAccountsInsteadOfAFakeBalance() {
        UUID userId = UUID.randomUUID();
        when(accountService.getAccounts(userId)).thenReturn(List.of());

        ChatSupportUseCase.Result result = chatSupportUseCase.execute(userId, "cual es mi saldo");

        assertThat(result.message()).contains("couldn't find any accounts");
    }

    @Test
    void answersTransferLimitWithoutTouchingAccounts() {
        ChatSupportUseCase.Result result = chatSupportUseCase.execute(UUID.randomUUID(), "what's my transfer limit?");

        assertThat(result.intent()).isEqualTo("TransferLimit");
        assertThat(result.message()).contains("50,000 MXN");
    }

    @Test
    void fallsBackWhenNothingMatches() {
        ChatSupportUseCase.Result result = chatSupportUseCase.execute(UUID.randomUUID(), "asdkjaslkdj");

        assertThat(result.intent()).isEqualTo("FallbackIntent");
    }
}
