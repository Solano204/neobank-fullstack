package neobank.application.usecase.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountResponse;
import neobank.application.dto.response.SpendingAnalyticsResponse;
import neobank.application.service.AccountService;
import neobank.infrastructure.adapter.dynamodb.TransactionHistoryAdapter;
import neobank.infrastructure.adapter.dynamodb.TransactionRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Computed from the same "transactions" DynamoDB data the transaction
 * Lambdas write — no fabricated numbers. The only categorization axis the
 * data actually carries is transaction status, so that's what "categories"
 * breaks spending down by, rather than inventing categories (groceries,
 * entertainment, ...) nothing in the system has ever classified a
 * transaction into.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetSpendingAnalyticsUseCase {

    private final AccountService accountService;
    private final TransactionHistoryAdapter transactionHistoryAdapter;

    private static final List<String> CATEGORY_COLORS = List.of("#ef4444", "#f59e0b", "#3b82f6", "#8b5cf6");

    public SpendingAnalyticsResponse execute(UUID userId, String period) {
        int days = switch (period == null ? "MONTH" : period.toUpperCase(Locale.ROOT)) {
            case "WEEK" -> 7;
            case "QUARTER" -> 90;
            case "YEAR" -> 365;
            default -> 30;
        };

        long since = Instant.now().minus(days, ChronoUnit.DAYS).toEpochMilli();

        List<AccountResponse> accounts = accountService.getAccounts(userId);
        List<TransactionRecord> transactions = new ArrayList<>();
        for (AccountResponse account : accounts) {
            transactions.addAll(transactionHistoryAdapter.findForAccount(account.getAccountNumber(), since));
        }

        BigDecimal totalSpent = sum(transactions, t -> !t.incoming());
        BigDecimal totalReceived = sum(transactions, TransactionRecord::incoming);

        List<SpendingAnalyticsResponse.Category> categories = buildCategories(transactions);
        List<SpendingAnalyticsResponse.MonthlyPoint> monthlyData = buildTimeSeries(transactions, days);

        return SpendingAnalyticsResponse.builder()
                .period(period == null ? "MONTH" : period.toUpperCase(Locale.ROOT))
                .totalSpent(totalSpent)
                .totalReceived(totalReceived)
                .categories(categories)
                .monthlyData(monthlyData)
                .build();
    }

    private BigDecimal sum(List<TransactionRecord> transactions, java.util.function.Predicate<TransactionRecord> filter) {
        return transactions.stream()
                .filter(filter)
                .map(TransactionRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<SpendingAnalyticsResponse.Category> buildCategories(List<TransactionRecord> transactions) {
        Map<String, BigDecimal> byStatus = new LinkedHashMap<>();
        for (TransactionRecord t : transactions) {
            if (t.incoming()) continue;
            byStatus.merge(t.status().isEmpty() ? "UNKNOWN" : t.status(), t.amount(), BigDecimal::add);
        }

        BigDecimal total = byStatus.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SpendingAnalyticsResponse.Category> categories = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, BigDecimal> entry : byStatus.entrySet()) {
            double percentage = total.signum() == 0 ? 0
                    : entry.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(total, 1, RoundingMode.HALF_UP).doubleValue();
            categories.add(SpendingAnalyticsResponse.Category.builder()
                    .name(humanizeStatus(entry.getKey()))
                    .amount(entry.getValue())
                    .percentage(percentage)
                    .color(CATEGORY_COLORS.get(i % CATEGORY_COLORS.size()))
                    .build());
            i++;
        }
        return categories;
    }

    private static final class Bucket {
        String label;
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
    }

    private List<SpendingAnalyticsResponse.MonthlyPoint> buildTimeSeries(List<TransactionRecord> transactions, int days) {
        boolean byDay = days <= 31;
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern(byDay ? "MMM d" : "MMM yyyy");

        Map<String, Bucket> buckets = new TreeMap<>();
        for (TransactionRecord t : transactions) {
            var dateTime = Instant.ofEpochMilli(t.timestamp()).atZone(ZoneOffset.UTC);
            String bucketKey = byDay
                    ? dateTime.toLocalDate().toString()
                    : String.format("%04d-%02d", dateTime.getYear(), dateTime.getMonthValue());

            Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> new Bucket());
            bucket.label = dateTime.format(labelFormat);
            if (t.incoming()) {
                bucket.received = bucket.received.add(t.amount());
            } else {
                bucket.spent = bucket.spent.add(t.amount());
            }
        }

        List<SpendingAnalyticsResponse.MonthlyPoint> points = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            points.add(SpendingAnalyticsResponse.MonthlyPoint.builder()
                    .month(bucket.label)
                    .spent(bucket.spent)
                    .received(bucket.received)
                    .build());
        }
        return points;
    }

    private String humanizeStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> "Completadas";
            case "PENDING" -> "Pendientes";
            case "FAILED" -> "Fallidas";
            case "FROZEN_FRAUD" -> "Congeladas";
            default -> status;
        };
    }
}
