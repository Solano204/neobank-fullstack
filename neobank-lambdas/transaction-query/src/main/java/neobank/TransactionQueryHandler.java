package neobank;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class TransactionQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) {
                queryParams = new HashMap<>();
            }

            String accountNumber = queryParams.getOrDefault("account", "");
            int limit = Integer.parseInt(queryParams.getOrDefault("limit", "20"));
            int page = Integer.parseInt(queryParams.getOrDefault("page", "1"));

            if (accountNumber.isEmpty()) {
                return createErrorResponse(400, "MISSING_PARAMETER", "account parameter is required");
            }

            List<Map<String, Object>> transactions = queryTransactions(accountNumber, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("transactions", transactions);
            response.put("pagination", Map.of(
                    "current_page", page,
                    "per_page", limit,
                    "total_count", transactions.size()
            ));

            return createResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to fetch transactions");
        }
    }

    private List<Map<String, Object>> queryTransactions(String accountNumber, int limit) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("from_account-timestamp-index")
                .keyConditionExpression("from_account = :account")
                .expressionAttributeValues(Map.of(
                        ":account", AttributeValue.builder().s(accountNumber).build()
                ))
                .scanIndexForward(false)
                .limit(limit)
                .build();

        QueryResponse response = dynamoDb.query(request);

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("id", getStringValue(item, "transaction_id"));
            transaction.put("timestamp", getLongValue(item, "timestamp"));
            transaction.put("type", "TRANSFER_OUT");
            transaction.put("from_account", getStringValue(item, "from_account"));
            transaction.put("to_account", getStringValue(item, "to_account"));
            transaction.put("amount", -1 * getDoubleValue(item, "amount"));
            transaction.put("currency", "MXN");
            transaction.put("status", getStringValue(item, "status"));
            transaction.put("description", getStringValue(item, "description"));

            if (item.containsKey("balance_after")) {
                transaction.put("balance_after", getDoubleValue(item, "balance_after"));
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    private String getStringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private Long getLongValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.n() != null ? Long.parseLong(value.n()) : 0L;
    }

    private Double getDoubleValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.n() != null ? Double.parseDouble(value.n()) : 0.0;
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"INTERNAL_ERROR\"}");
        }
    }

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", error);
        errorBody.put("message", message);
        return createResponse(statusCode, errorBody);
    }
}