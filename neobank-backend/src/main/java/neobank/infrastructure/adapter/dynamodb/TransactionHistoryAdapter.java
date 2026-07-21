package neobank.infrastructure.adapter.dynamodb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.config.DynamoDbConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the same "transactions" DynamoDB table (and the same from_account /
 * to_account GSIs) the transaction-service and transaction-query Lambdas
 * write and read. Kept as a thin adapter so use cases stay storage-agnostic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionHistoryAdapter {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbConfig dynamoDbConfig;

    public List<TransactionRecord> findForAccount(String accountNumber, long sinceEpochMillis) {
        List<TransactionRecord> records = new ArrayList<>();
        records.addAll(query("from_account-timestamp-index", "from_account", accountNumber, sinceEpochMillis, false));
        records.addAll(query("to_account-timestamp-index", "to_account", accountNumber, sinceEpochMillis, true));
        return records;
    }

    private List<TransactionRecord> query(String indexName, String keyAttribute, String accountNumber,
                                           long sinceEpochMillis, boolean incoming) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(dynamoDbConfig.getTransactionsTable())
                    .indexName(indexName)
                    .keyConditionExpression(keyAttribute + " = :account AND #ts >= :since")
                    .expressionAttributeNames(Map.of("#ts", "timestamp"))
                    .expressionAttributeValues(Map.of(
                            ":account", AttributeValue.builder().s(accountNumber).build(),
                            ":since", AttributeValue.builder().n(String.valueOf(sinceEpochMillis)).build()
                    ))
                    .build());

            List<TransactionRecord> records = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                records.add(new TransactionRecord(
                        str(item, "transaction_id"),
                        Long.parseLong(str(item, "timestamp").isEmpty() ? "0" : str(item, "timestamp")),
                        str(item, "from_account"),
                        str(item, "to_account"),
                        new BigDecimal(str(item, "amount").isEmpty() ? "0" : str(item, "amount")),
                        str(item, "status"),
                        incoming
                ));
            }
            return records;
        } catch (Exception e) {
            log.error("Failed to query DynamoDB index {} for account {}: {}", indexName, accountNumber, e.getMessage());
            return List.of();
        }
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null) return "";
        if (value.s() != null) return value.s();
        if (value.n() != null) return value.n();
        return "";
    }
}
