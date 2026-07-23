package neobank.infrastructure.adapter.dynamodb;

import java.math.BigDecimal;

public record TransactionRecord(
        String transactionId,
        long timestamp,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String status,
        boolean incoming
) {
}
