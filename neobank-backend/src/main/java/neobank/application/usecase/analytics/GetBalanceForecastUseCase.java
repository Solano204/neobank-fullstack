package neobank.application.usecase.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountResponse;
import neobank.application.service.AccountService;
import neobank.infrastructure.adapter.dynamodb.TransactionHistoryAdapter;
import neobank.infrastructure.adapter.dynamodb.TransactionRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A simple linear projection off the last 30 days of real completed
 * transactions -- not a hardcoded number. Confidence is a plain function of
 * how much history actually backs the projection, not a fabricated constant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetBalanceForecastUseCase {

    private static final int LOOKBACK_DAYS = 30;

    private final AccountService accountService;
    private final TransactionHistoryAdapter transactionHistoryAdapter;

    public Map<String, Object> execute(UUID userId) {
        List<AccountResponse> accounts = accountService.getAccounts(userId);

        BigDecimal currentBalance = accounts.stream()
                .map(AccountResponse::getAvailableBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS).toEpochMilli();
        List<TransactionRecord> transactions = accounts.stream()
                .flatMap(a -> transactionHistoryAdapter.findForAccount(a.getAccountNumber(), since).stream())
                .filter(t -> "COMPLETED".equals(t.status()))
                .toList();

        BigDecimal netChange = transactions.stream()
                .map(t -> t.incoming() ? t.amount() : t.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dailyNetChange = netChange.divide(BigDecimal.valueOf(LOOKBACK_DAYS), 4, RoundingMode.HALF_UP);

        BigDecimal forecast7 = currentBalance.add(dailyNetChange.multiply(BigDecimal.valueOf(7)));
        BigDecimal forecast30 = currentBalance.add(dailyNetChange.multiply(BigDecimal.valueOf(30)));

        // More transaction history behind the projection -> more confidence, capped at 0.9
        // since a straight-line extrapolation is never more than "reasonably confident".
        double confidence = Math.min(0.9, transactions.size() / 30.0);

        return Map.of(
                "current_balance", currentBalance,
                "forecast_7_days", forecast7.setScale(2, RoundingMode.HALF_UP),
                "forecast_30_days", forecast30.setScale(2, RoundingMode.HALF_UP),
                "based_on", String.format("Average daily net change over the last %d days (%d completed transactions)", LOOKBACK_DAYS, transactions.size()),
                "confidence", Math.round(confidence * 100.0) / 100.0
        );
    }
}
