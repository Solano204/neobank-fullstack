package neobank.application.usecase.analytics;

import neobank.application.dto.response.AccountResponse;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBalanceForecastUseCaseTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionHistoryAdapter transactionHistoryAdapter;

    @InjectMocks
    private GetBalanceForecastUseCase getBalanceForecastUseCase;

    private final UUID userId = UUID.randomUUID();

    private AccountResponse account(String number, String availableBalance) {
        return AccountResponse.builder()
                .id(UUID.randomUUID())
                .accountNumber(number)
                .balance(new BigDecimal(availableBalance))
                .availableBalance(new BigDecimal(availableBalance))
                .currency("MXN")
                .build();
    }

    @Test
    void noTransactionHistory_forecastsFlatFromCurrentBalance() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00")));
        when(transactionHistoryAdapter.findForAccount(any(), anyLong())).thenReturn(List.of());

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        assertThat(forecast.get("current_balance")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(forecast.get("forecast_7_days")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(forecast.get("forecast_30_days")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(forecast.get("confidence")).isEqualTo(0.0);
    }

    @Test
    void sumsCurrentBalanceAcrossMultipleAccounts() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00"), account("ACC2", "500.00")));
        when(transactionHistoryAdapter.findForAccount(any(), anyLong())).thenReturn(List.of());

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        assertThat(forecast.get("current_balance")).isEqualTo(new BigDecimal("1500.00"));
    }

    @Test
    void positiveNetIncome_projectsBalanceGrowth() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00")));
        // 30 incoming transactions of 100 each over the lookback window -> net +3000 over 30 days = +100/day
        List<TransactionRecord> incoming = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new TransactionRecord("tx" + i, System.currentTimeMillis(), "OTHER", "ACC1", new BigDecimal("100.00"), "COMPLETED", true))
                .toList();
        when(transactionHistoryAdapter.findForAccount(org.mockito.ArgumentMatchers.eq("ACC1"), anyLong())).thenReturn(incoming);

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        assertThat((BigDecimal) forecast.get("forecast_7_days")).isGreaterThan(new BigDecimal("1000.00"));
        assertThat((BigDecimal) forecast.get("forecast_30_days")).isGreaterThan((BigDecimal) forecast.get("forecast_7_days"));
    }

    @Test
    void negativeNetOutflow_projectsBalanceDecline() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00")));
        List<TransactionRecord> outgoing = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new TransactionRecord("tx" + i, System.currentTimeMillis(), "ACC1", "OTHER", new BigDecimal("100.00"), "COMPLETED", false))
                .toList();
        when(transactionHistoryAdapter.findForAccount(org.mockito.ArgumentMatchers.eq("ACC1"), anyLong())).thenReturn(outgoing);

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        assertThat((BigDecimal) forecast.get("forecast_7_days")).isLessThan(new BigDecimal("1000.00"));
    }

    @Test
    void ignoresNonCompletedTransactionsWhenComputingNetChange() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00")));
        List<TransactionRecord> pending = List.of(
                new TransactionRecord("tx1", System.currentTimeMillis(), "OTHER", "ACC1", new BigDecimal("5000.00"), "PENDING", true));
        when(transactionHistoryAdapter.findForAccount(org.mockito.ArgumentMatchers.eq("ACC1"), anyLong())).thenReturn(pending);

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        // A huge pending (not completed) transaction must not move the forecast at all
        assertThat(forecast.get("forecast_7_days")).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void confidenceIsCappedAtNinetyPercentEvenWithLotsOfHistory() {
        when(accountService.getAccounts(userId)).thenReturn(List.of(account("ACC1", "1000.00")));
        List<TransactionRecord> lotsOfHistory = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new TransactionRecord("tx" + i, System.currentTimeMillis(), "OTHER", "ACC1", new BigDecimal("10.00"), "COMPLETED", true))
                .toList();
        when(transactionHistoryAdapter.findForAccount(org.mockito.ArgumentMatchers.eq("ACC1"), anyLong())).thenReturn(lotsOfHistory);

        Map<String, Object> forecast = getBalanceForecastUseCase.execute(userId);

        assertThat((Double) forecast.get("confidence")).isEqualTo(0.9);
    }
}
