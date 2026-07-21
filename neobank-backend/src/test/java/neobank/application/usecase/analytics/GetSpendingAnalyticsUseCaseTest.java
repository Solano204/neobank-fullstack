package neobank.application.usecase.analytics;

import neobank.application.dto.response.AccountResponse;
import neobank.application.dto.response.SpendingAnalyticsResponse;
import neobank.application.service.AccountService;
import neobank.infrastructure.adapter.dynamodb.TransactionHistoryAdapter;
import neobank.infrastructure.adapter.dynamodb.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSpendingAnalyticsUseCaseTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionHistoryAdapter transactionHistoryAdapter;

    @InjectMocks
    private GetSpendingAnalyticsUseCase getSpendingAnalyticsUseCase;

    @Test
    void computesTotalsFromRealTransactionsNotHardcodedNumbers() {
        UUID userId = UUID.randomUUID();
        when(accountService.getAccounts(userId)).thenReturn(List.of(
                AccountResponse.builder().accountNumber("111111111111111111").build()
        ));
        when(transactionHistoryAdapter.findForAccount(eq("111111111111111111"), anyLong())).thenReturn(List.of(
                new TransactionRecord("txn_1", 1000L, "111111111111111111", "222222222222222222",
                        new BigDecimal("300.00"), "COMPLETED", false),
                new TransactionRecord("txn_2", 2000L, "333333333333333333", "111111111111111111",
                        new BigDecimal("500.00"), "COMPLETED", true)
        ));

        SpendingAnalyticsResponse response = getSpendingAnalyticsUseCase.execute(userId, "month");

        assertThat(response.getTotalSpent()).isEqualByComparingTo("300.00");
        assertThat(response.getTotalReceived()).isEqualByComparingTo("500.00");
    }

    @Test
    void returnsZeroTotalsWhenThereIsNoHistoryInsteadOfFakeData() {
        UUID userId = UUID.randomUUID();
        when(accountService.getAccounts(userId)).thenReturn(List.of(
                AccountResponse.builder().accountNumber("111111111111111111").build()
        ));
        when(transactionHistoryAdapter.findForAccount(eq("111111111111111111"), anyLong())).thenReturn(List.of());

        SpendingAnalyticsResponse response = getSpendingAnalyticsUseCase.execute(userId, "week");

        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalReceived()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCategories()).isEmpty();
    }

    @Test
    void breaksDownSpendingByTransactionStatus() {
        UUID userId = UUID.randomUUID();
        when(accountService.getAccounts(userId)).thenReturn(List.of(
                AccountResponse.builder().accountNumber("111111111111111111").build()
        ));
        when(transactionHistoryAdapter.findForAccount(eq("111111111111111111"), anyLong())).thenReturn(List.of(
                new TransactionRecord("txn_1", 1000L, "111111111111111111", "222222222222222222",
                        new BigDecimal("100.00"), "COMPLETED", false),
                new TransactionRecord("txn_2", 1500L, "111111111111111111", "222222222222222222",
                        new BigDecimal("50.00"), "FAILED", false)
        ));

        SpendingAnalyticsResponse response = getSpendingAnalyticsUseCase.execute(userId, "month");

        assertThat(response.getCategories())
                .extracting(SpendingAnalyticsResponse.Category::getName)
                .containsExactlyInAnyOrder("Completadas", "Fallidas");
    }
}
