package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class LedgerWriterHandler implements RequestHandler<SQSEvent, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        int successCount = 0;
        int failureCount = 0;

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                Map<String, Object> transaction = objectMapper.readValue(
                        message.getBody(),
                        Map.class
                );

                writeToLedger(transaction, context);
                successCount++;

                context.getLogger().log("Written to ledger: " + transaction.get("transaction_id"));

            } catch (Exception e) {
                failureCount++;
                context.getLogger().log("Error writing to ledger: " + e.getMessage());
                e.printStackTrace();
            }
        }

        context.getLogger().log(String.format(
                "Ledger write complete. Success: %d, Failures: %d",
                successCount, failureCount
        ));

        return "SUCCESS";
    }

    private void writeToLedger(Map<String, Object> transaction, Context context) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("transaction_id",
                AttributeValue.builder().s(getString(transaction, "transaction_id")).build());

        item.put("timestamp",
                AttributeValue.builder().n(String.valueOf(getLong(transaction, "timestamp"))).build());

        item.put("type",
                AttributeValue.builder().s(getString(transaction, "type", "TRANSFER")).build());

        item.put("from_account",
                AttributeValue.builder().s(getString(transaction, "from_account")).build());

        item.put("to_account",
                AttributeValue.builder().s(getString(transaction, "to_account")).build());

        item.put("amount",
                AttributeValue.builder().n(String.valueOf(getDouble(transaction, "amount"))).build());

        item.put("currency",
                AttributeValue.builder().s(getString(transaction, "currency", "MXN")).build());

        item.put("status",
                AttributeValue.builder().s(getString(transaction, "status")).build());

        if (transaction.containsKey("description")) {
            item.put("description",
                    AttributeValue.builder().s(getString(transaction, "description")).build());
        }

        if (transaction.containsKey("reference")) {
            item.put("reference",
                    AttributeValue.builder().s(getString(transaction, "reference")).build());
        }

        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put("created_at",
                AttributeValue.builder().s(new Date().toString()).build());
        metadata.put("source",
                AttributeValue.builder().s("transaction-service").build());

        item.put("metadata", AttributeValue.builder().m(metadata).build());

        try {
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDb.putItem(request);

        } catch (DynamoDbException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            throw e;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}