package neobank;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class TransactionQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static HikariDataSource dataSource;

    static {
        if (DB_URL != null && !DB_URL.isEmpty()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
            dataSource = new HikariDataSource(config);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String callerSub = extractCognitoSub(input);
            if (callerSub == null || callerSub.isEmpty()) {
                return createErrorResponse(401, "UNAUTHORIZED", "Missing or invalid caller identity");
            }

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

            if (!isOwnedByCaller(callerSub, accountNumber, context)) {
                return createErrorResponse(403, "FORBIDDEN", "account does not belong to the authenticated caller");
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

    @SuppressWarnings("unchecked")
    String extractCognitoSub(APIGatewayProxyRequestEvent input) {
        try {
            Object authorizer = input.getRequestContext().getAuthorizer();
            if (!(authorizer instanceof Map)) return null;
            Object claims = ((Map<String, Object>) authorizer).get("claims");
            if (!(claims instanceof Map)) return null;
            Object sub = ((Map<String, Object>) claims).get("sub");
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    boolean isOwnedByCaller(String callerSub, String accountNumber, Context context) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM accounts a JOIN users u ON a.user_id = u.id " +
                            "WHERE a.account_number = ? AND u.cognito_user_id = ?"
            );
            stmt.setString(1, accountNumber);
            stmt.setString(2, callerSub);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            context.getLogger().log("Ownership check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Money moves both ways: an account can be from_account (outgoing) or
     * to_account (incoming) on any given transfer, so both GSIs have to be
     * queried and merged -- querying from_account alone only ever shows
     * transfers the account sent, never the ones it received.
     */
    private List<Map<String, Object>> queryTransactions(String accountNumber, int limit) {
        List<Map<String, Object>> outgoing = queryByIndex(
                "from_account-timestamp-index", "from_account", accountNumber, limit, "TRANSFER_OUT");
        List<Map<String, Object>> incoming = queryByIndex(
                "to_account-timestamp-index", "to_account", accountNumber, limit, "TRANSFER_IN");

        List<Map<String, Object>> merged = new ArrayList<>(outgoing.size() + incoming.size());
        merged.addAll(outgoing);
        merged.addAll(incoming);
        merged.sort((a, b) -> Long.compare((Long) b.get("timestamp"), (Long) a.get("timestamp")));

        return merged.size() > limit ? merged.subList(0, limit) : merged;
    }

    private List<Map<String, Object>> queryByIndex(String indexName, String keyAttribute,
                                                     String accountNumber, int limit, String direction) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(indexName)
                .keyConditionExpression(keyAttribute + " = :account")
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
            transaction.put("type", direction);
            transaction.put("from_account", getStringValue(item, "from_account"));
            transaction.put("to_account", getStringValue(item, "to_account"));
            // Was double amount = Double.parseDouble(...) - the exact float
            // round-trip that ledger-writer/TransactionHandler were
            // deliberately written with BigDecimal to avoid (see their
            // comments), undone at this read boundary. Transaction history
            // shown to the user could display drifted amounts even though
            // the actual stored balance was never wrong.
            BigDecimal amount = getBigDecimalValue(item, "amount");
            transaction.put("amount", "TRANSFER_OUT".equals(direction) ? amount.negate() : amount);
            transaction.put("currency", "MXN");
            transaction.put("status", getStringValue(item, "status"));
            transaction.put("description", getStringValue(item, "description"));

            if (item.containsKey("balance_after")) {
                transaction.put("balance_after", getBigDecimalValue(item, "balance_after"));
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    String getStringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    Long getLongValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.n() != null ? Long.parseLong(value.n()) : 0L;
    }

    BigDecimal getBigDecimalValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.n() != null ? new BigDecimal(value.n()) : BigDecimal.ZERO;
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
