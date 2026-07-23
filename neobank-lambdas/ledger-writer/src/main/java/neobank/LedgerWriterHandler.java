package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.util.*;

public class LedgerWriterHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    // This is the ledger of record -- deserialize the JSON "amount" as
    // BigDecimal instead of Jackson's default double, so writing it back out
    // to DynamoDB doesn't round-trip through floating point.
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                Map<String, Object> transaction = objectMapper.readValue(
                        message.getBody(),
                        Map.class
                );

                writeToLedger(transaction, context);

                context.getLogger().log("Written to ledger: " + transaction.get("transaction_id"));

            } catch (Exception e) {
                context.getLogger().log("Error writing to ledger: " + e.getMessage());
                e.printStackTrace();
                // Only this message gets redelivered/retried (and eventually
                // DLQ'd after maxReceiveCount) -- the rest of the batch that
                // already succeeded is not reprocessed.
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        context.getLogger().log(String.format(
                "Ledger write complete. Success: %d, Failures: %d",
                event.getRecords().size() - failures.size(), failures.size()
        ));

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    /**
     * transaction-service fans this same transaction out to fraud-checker,
     * analytics-processor and notification-service in parallel via separate
     * SQS queues, with no ordering guarantee between them. fraud-checker
     * writes fraud_score/fraud_checked_at (and, on a frozen transaction,
     * status=FROZEN_FRAUD) to the SAME item, keyed by the same
     * transaction_id. A PutItem here would replace the whole item and
     * silently erase whatever fraud-checker already wrote if it landed
     * first -- so this has to be a partial UpdateItem instead, touching
     * only the ledger's own fields.
     */
    private void writeToLedger(Map<String, Object> transaction, Context context) {
        String transactionId = getString(transaction, "transaction_id");
        String status = getString(transaction, "status");

        Map<String, AttributeValue> values = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        List<String> setClauses = new ArrayList<>();

        setClauses.add("#timestamp = :timestamp");
        names.put("#timestamp", "timestamp");
        values.put(":timestamp", AttributeValue.builder().n(String.valueOf(getLong(transaction, "timestamp"))).build());

        setClauses.add("#type = :type");
        names.put("#type", "type");
        values.put(":type", AttributeValue.builder().s(getString(transaction, "type", "TRANSFER")).build());

        setClauses.add("from_account = :from_account");
        values.put(":from_account", AttributeValue.builder().s(getString(transaction, "from_account")).build());

        setClauses.add("to_account = :to_account");
        values.put(":to_account", AttributeValue.builder().s(getString(transaction, "to_account")).build());

        setClauses.add("amount = :amount");
        values.put(":amount", AttributeValue.builder().n(getBigDecimal(transaction, "amount").toPlainString()).build());

        setClauses.add("currency = :currency");
        values.put(":currency", AttributeValue.builder().s(getString(transaction, "currency", "MXN")).build());

        if (transaction.containsKey("description")) {
            setClauses.add("description = :description");
            values.put(":description", AttributeValue.builder().s(getString(transaction, "description")).build());
        }

        if (transaction.containsKey("reference")) {
            setClauses.add("reference = :reference");
            values.put(":reference", AttributeValue.builder().s(getString(transaction, "reference")).build());
        }

        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put("created_at", AttributeValue.builder().s(new Date().toString()).build());
        metadata.put("source", AttributeValue.builder().s("transaction-service").build());
        setClauses.add("metadata = :metadata");
        values.put(":metadata", AttributeValue.builder().m(metadata).build());

        // #status is set in its own clause, guarded so it can never overwrite
        // a fraud freeze that already landed.
        List<String> withStatus = new ArrayList<>(setClauses);
        withStatus.add("#status = :status");
        Map<String, AttributeValue> withStatusValues = new HashMap<>(values);
        withStatusValues.put(":status", AttributeValue.builder().s(status).build());
        withStatusValues.put(":frozen", AttributeValue.builder().s("FROZEN_FRAUD").build());
        Map<String, String> withStatusNames = new HashMap<>(names);
        withStatusNames.put("#status", "status");

        try {
            dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("transaction_id", AttributeValue.builder().s(transactionId).build()))
                    .updateExpression("SET " + String.join(", ", withStatus))
                    .conditionExpression("attribute_not_exists(#status) OR #status <> :frozen")
                    .expressionAttributeNames(withStatusNames)
                    .expressionAttributeValues(withStatusValues)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // fraud-checker already froze this transaction -- write every
            // other ledger field but leave status alone.
            context.getLogger().log("Transaction " + transactionId + " already FROZEN_FRAUD; writing ledger fields without touching status");
            dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("transaction_id", AttributeValue.builder().s(transactionId).build()))
                    .updateExpression("SET " + String.join(", ", setClauses))
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());
        } catch (DynamoDbException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            throw e;
        }
    }

    String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}