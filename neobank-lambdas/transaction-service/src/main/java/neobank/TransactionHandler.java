package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TransactionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000");

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String SNS_TXN_TOPIC_ARN = System.getenv("SNS_TXN_TOPIC_ARN");
    private static final SnsClient snsClient = SnsClient.create();
    private static HikariDataSource dataSource;

    static {
        if (DB_URL != null && !DB_URL.isEmpty()) {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(DB_URL);
                config.setUsername(DB_USER);
                config.setPassword(DB_PASSWORD);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                dataSource = new HikariDataSource(config);
            } catch (Exception e) {
                System.err.println("FATAL: Failed to initialize DB connection pool: " + e.getMessage());
                throw e;
            }
        }
    }

    // alreadyProcessed distinguishes a fresh transfer from an idempotency-key
    // replay - handleRequest uses it to skip re-publishing to SNS on a
    // replay, so a retried request doesn't cause ledger-writer/fraud-checker/
    // analytics-processor/notification-service to all process the same
    // underlying transfer a second time.
    private record TransferResult(String transactionId, BigDecimal newBalance, boolean alreadyProcessed) {}

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Transaction request received");
        context.getLogger().log("SNS_TXN_TOPIC_ARN configured: " + (SNS_TXN_TOPIC_ARN != null ? SNS_TXN_TOPIC_ARN : "NULL - MISSING ENV VAR"));
        context.getLogger().log("DB_URL configured: " + (DB_URL != null ? "YES" : "NULL - MISSING ENV VAR"));

        try {
            String callerSub = extractCognitoSub(input);
            if (callerSub == null || callerSub.isEmpty()) {
                return createErrorResponse(401, "UNAUTHORIZED", "Missing or invalid caller identity", null);
            }

            if (input.getBody() == null || input.getBody().isEmpty()) {
                return createErrorResponse(400, "INVALID_REQUEST", "Request body is required", null);
            }

            TransactionRequest request = objectMapper.readValue(input.getBody(), TransactionRequest.class);
            context.getLogger().log("Processing transfer: " + request.getFromAccount() + " -> " + request.getToAccount() + " amount: " + request.getAmount());

            validateRequest(request);

            // Was no idempotency protection at all: a client retry after a
            // timeout (the caller genuinely can't tell whether the transfer
            // succeeded or just the response was lost) executed a second,
            // distinct transfer - real money moved twice. Optional: a
            // caller not sending this header gets the old at-most-once-per-
            // request behavior unchanged, but every client should send one
            // on a money-movement call.
            String idempotencyKey = extractIdempotencyKey(input);

            TransferResult result = executeTransfer(
                    callerSub,
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getDescription(),
                    idempotencyKey,
                    context
            );

            context.getLogger().log("Transfer successful. Transaction ID: " + result.transactionId());

            // Skip re-publishing on an idempotency-key replay (result.alreadyProcessed())
            // - otherwise a retried request would still cause ledger-writer/
            // fraud-checker/analytics-processor/notification-service to all
            // process the same underlying transfer a second time, even
            // though the transfer itself was correctly deduplicated above.
            if (!result.alreadyProcessed()) {
                publishToTopic(
                        result.transactionId(),
                        request.getFromAccount(),
                        request.getToAccount(),
                        request.getAmount(),
                        request.getDescription(),
                        context
                );
            }

            TransactionResponse response = new TransactionResponse(
                    "SUCCESS",
                    result.transactionId(),
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    result.newBalance()
            );

            return createResponse(200, response);

        } catch (InsufficientFundsException e) {
            context.getLogger().log("Insufficient funds: " + e.getMessage());
            return createErrorResponse(400, "INSUFFICIENT_FUNDS", e.getMessage(),
                    Map.of("current_balance", e.getCurrentBalance(), "required_amount", e.getRequiredAmount()));
        } catch (ForbiddenException e) {
            context.getLogger().log("Forbidden: " + e.getMessage());
            return createErrorResponse(403, "FORBIDDEN", e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            context.getLogger().log("Validation error: " + e.getMessage());
            return createErrorResponse(400, "VALIDATION_ERROR", e.getMessage(), null);
        } catch (Exception e) {
            context.getLogger().log("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            return createErrorResponse(500, "INTERNAL_ERROR", e.getMessage(), null);
        }
    }

    @SuppressWarnings("unchecked")
    String extractCognitoSub(APIGatewayProxyRequestEvent input) {
        try {
            // API Gateway injects the decoded Cognito claims here once the
            // COGNITO_USER_POOLS authorizer has already verified the JWT —
            // we don't re-validate the signature, just read who it belongs to.
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

    String extractIdempotencyKey(APIGatewayProxyRequestEvent input) {
        Map<String, String> headers = input.getHeaders();
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Idempotency-Key")) {
                String value = entry.getValue();
                return (value == null || value.isBlank()) ? null : value.trim();
            }
        }
        return null;
    }

    void validateRequest(TransactionRequest request) {
        if (request.getFromAccount() == null || request.getFromAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("from_account is required");
        }
        if (request.getToAccount() == null || request.getToAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("to_account is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        if (request.getAmount().compareTo(DAILY_LIMIT) > 0) {
            throw new IllegalArgumentException("amount exceeds daily limit of 50,000 MXN");
        }
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new IllegalArgumentException("cannot transfer to same account");
        }
    }

    private TransferResult executeTransfer(String callerSub, String fromAccount, String toAccount,
                                            BigDecimal amount, String description, String idempotencyKey,
                                            Context context) throws Exception {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            context.getLogger().log("DB connection acquired, starting transaction");

            if (idempotencyKey != null) {
                TransferResult cached = findCachedResult(conn, idempotencyKey);
                if (cached != null) {
                    context.getLogger().log("Idempotency-Key " + idempotencyKey + " already processed, returning cached result");
                    conn.commit();
                    return cached;
                }
            }

            PreparedStatement lockStmt = conn.prepareStatement(
                    "SELECT a.balance, a.available_balance, a.status, u.cognito_user_id FROM accounts a " +
                            "JOIN users u ON a.user_id = u.id " +
                            "WHERE a.account_number = ? FOR UPDATE"
            );
            lockStmt.setString(1, fromAccount);
            ResultSet rs = lockStmt.executeQuery();

            if (!rs.next()) {
                throw new Exception("Sender account not found: " + fromAccount);
            }

            BigDecimal currentBalance = rs.getBigDecimal("balance");
            BigDecimal availableBalance = rs.getBigDecimal("available_balance");
            String status = rs.getString("status");
            String ownerCognitoId = rs.getString("cognito_user_id");

            if (!callerSub.equals(ownerCognitoId)) {
                throw new ForbiddenException("from_account does not belong to the authenticated caller");
            }

            context.getLogger().log("Sender balance: " + currentBalance + ", status: " + status);

            if (!"ACTIVE".equals(status)) {
                throw new Exception("Account is " + status + ". Cannot process transaction.");
            }

            if (availableBalance.compareTo(amount) < 0) {
                throw new InsufficientFundsException(availableBalance, amount);
            }

            PreparedStatement deductStmt = conn.prepareStatement(
                    "UPDATE accounts SET " +
                            "balance = balance - ?, " +
                            "available_balance = available_balance - ?, " +
                            "updated_at = NOW(), " +
                            "last_transaction_at = NOW() " +
                            "WHERE account_number = ?"
            );
            deductStmt.setBigDecimal(1, amount);
            deductStmt.setBigDecimal(2, amount);
            deductStmt.setString(3, fromAccount);
            deductStmt.executeUpdate();
            context.getLogger().log("Deducted " + amount + " from sender");

            PreparedStatement addStmt = conn.prepareStatement(
                    "UPDATE accounts SET " +
                            "balance = balance + ?, " +
                            "available_balance = available_balance + ?, " +
                            "updated_at = NOW(), " +
                            "last_transaction_at = NOW() " +
                            "WHERE account_number = ?"
            );
            addStmt.setBigDecimal(1, amount);
            addStmt.setBigDecimal(2, amount);
            addStmt.setString(3, toAccount);
            int updated = addStmt.executeUpdate();

            if (updated == 0) {
                throw new Exception("Recipient account not found: " + toAccount);
            }
            context.getLogger().log("Added " + amount + " to recipient");

            BigDecimal newBalance = currentBalance.subtract(amount);
            // Was "txn_" + System.currentTimeMillis() + "_" + last-4-digits-
            // of-account - only millisecond resolution, so two transfers
            // from the same account within the same millisecond (real,
            // plausible under concurrent/scripted load) collided on the
            // same transaction_id. Downstream consumers (ledger-writer
            // especially) key their conditional DynamoDB writes off this ID
            // - a collision there means one transaction's ledger entry can
            // clobber another's. SecureRandom-backed suffix instead.
            String transactionId = "txn_" + System.currentTimeMillis() + "_" +
                    String.format("%08x", RANDOM.nextInt());

            if (idempotencyKey != null) {
                try {
                    PreparedStatement insertIdempotency = conn.prepareStatement(
                            "INSERT INTO transaction_idempotency_keys " +
                                    "(idempotency_key, transaction_id, from_account, to_account, amount, new_balance) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)"
                    );
                    insertIdempotency.setString(1, idempotencyKey);
                    insertIdempotency.setString(2, transactionId);
                    insertIdempotency.setString(3, fromAccount);
                    insertIdempotency.setString(4, toAccount);
                    insertIdempotency.setBigDecimal(5, amount);
                    insertIdempotency.setBigDecimal(6, newBalance);
                    insertIdempotency.executeUpdate();
                } catch (SQLException e) {
                    // 23505 = unique_violation (Postgres): a concurrent request
                    // with the same Idempotency-Key won the race and already
                    // inserted this key. Roll back this transaction's balance
                    // updates entirely (they must not both commit) and return
                    // the winner's already-committed result instead of failing.
                    if ("23505".equals(e.getSQLState())) {
                        conn.rollback();
                        context.getLogger().log("Idempotency-Key " + idempotencyKey +
                                " lost a concurrent race, returning the winning request's result");
                        try (Connection readConn = dataSource.getConnection()) {
                            TransferResult winner = findCachedResult(readConn, idempotencyKey);
                            if (winner != null) {
                                return winner;
                            }
                        }
                        throw e;
                    }
                    throw e;
                }
            }

            conn.commit();
            context.getLogger().log("Transaction committed successfully");

            return new TransferResult(transactionId, newBalance, false);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    context.getLogger().log("Transaction rolled back due to: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    context.getLogger().log("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    context.getLogger().log("Connection close error: " + closeEx.getMessage());
                }
            }
        }
    }

    private TransferResult findCachedResult(Connection conn, String idempotencyKey) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT transaction_id, new_balance FROM transaction_idempotency_keys WHERE idempotency_key = ?"
        );
        stmt.setString(1, idempotencyKey);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return new TransferResult(rs.getString("transaction_id"), rs.getBigDecimal("new_balance"), true);
        }
        return null;
    }

    private void publishToTopic(String transactionId, String fromAccount,
                                String toAccount, BigDecimal amount, String description,
                                Context context) {
        // Check SNS_TXN_TOPIC_ARN before attempting
        if (SNS_TXN_TOPIC_ARN == null || SNS_TXN_TOPIC_ARN.isEmpty()) {
            context.getLogger().log("ERROR: SNS_TXN_TOPIC_ARN environment variable is not set! Publish skipped.");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("transaction_id", transactionId);
            message.put("from_account", fromAccount);
            message.put("to_account", toAccount);
            // Kept as a BigDecimal (Jackson serializes it as a plain JSON
            // number) rather than a string — every consumer's getDouble()
            // only recognizes a numeric amount and silently reads 0.0 for
            // anything that isn't `instanceof Number`.
            message.put("amount", amount);
            message.put("description", description != null ? description : "");
            message.put("status", "COMPLETED");
            message.put("timestamp", System.currentTimeMillis());
            message.put("type", "TRANSFER");
            message.put("currency", "MXN");

            String messageBody = objectMapper.writeValueAsString(message);
            context.getLogger().log("Publishing to SNS topic: " + SNS_TXN_TOPIC_ARN);
            context.getLogger().log("Message body: " + messageBody);

            // Fans out to every subscribed queue (ledger, fraud, analytics,
            // notifications) instead of landing on a single SQS queue that only
            // one of those consumers would ever actually receive.
            PublishResponse snsResponse = snsClient.publish(PublishRequest.builder()
                    .topicArn(SNS_TXN_TOPIC_ARN)
                    .message(messageBody)
                    .build()
            );

            context.getLogger().log("SNS message published successfully! MessageId: " + snsResponse.messageId());

        } catch (Exception e) {
            // Log the full error — do NOT swallow it silently
            context.getLogger().log("ERROR publishing to SNS: " + e.getMessage());
            context.getLogger().log("ERROR type: " + e.getClass().getName());
            e.printStackTrace();
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
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

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error,
                                                             String message, Object data) {
        try {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", error);
            errorBody.put("message", message);
            if (data != null) {
                errorBody.put("data", data);
            }
            return createResponse(statusCode, errorBody);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"INTERNAL_ERROR\"}");
        }
    }
}
