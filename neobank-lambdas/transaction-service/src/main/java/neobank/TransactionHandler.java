package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TransactionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String QUEUE_URL = System.getenv("QUEUE_URL");
    private static final SqsClient sqsClient = SqsClient.create();
    private static HikariDataSource dataSource;

    static {
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

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Transaction request received");
        context.getLogger().log("QUEUE_URL configured: " + (QUEUE_URL != null ? QUEUE_URL : "NULL - MISSING ENV VAR"));
        context.getLogger().log("DB_URL configured: " + (DB_URL != null ? "YES" : "NULL - MISSING ENV VAR"));

        try {
            if (input.getBody() == null || input.getBody().isEmpty()) {
                return createErrorResponse(400, "INVALID_REQUEST", "Request body is required", null);
            }

            TransactionRequest request = objectMapper.readValue(input.getBody(), TransactionRequest.class);
            context.getLogger().log("Processing transfer: " + request.getFromAccount() + " -> " + request.getToAccount() + " amount: " + request.getAmount());

            validateRequest(request);

            Map<String, String> result = executeTransfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getDescription(),
                    context
            );

            context.getLogger().log("Transfer successful. Transaction ID: " + result.get("transactionId"));

            // Publish to SQS — now with proper logging
            publishToQueue(
                    result.get("transactionId"),
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getDescription(),
                    context
            );

            TransactionResponse response = new TransactionResponse(
                    "SUCCESS",
                    result.get("transactionId"),
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    Double.parseDouble(result.get("newBalance"))
            );

            return createResponse(200, response);

        } catch (InsufficientFundsException e) {
            context.getLogger().log("Insufficient funds: " + e.getMessage());
            return createErrorResponse(400, "INSUFFICIENT_FUNDS", e.getMessage(),
                    Map.of("current_balance", e.getCurrentBalance(), "required_amount", e.getRequiredAmount()));
        } catch (IllegalArgumentException e) {
            context.getLogger().log("Validation error: " + e.getMessage());
            return createErrorResponse(400, "VALIDATION_ERROR", e.getMessage(), null);
        } catch (Exception e) {
            context.getLogger().log("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            return createErrorResponse(500, "INTERNAL_ERROR", e.getMessage(), null);
        }
    }

    private void validateRequest(TransactionRequest request) {
        if (request.getFromAccount() == null || request.getFromAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("from_account is required");
        }
        if (request.getToAccount() == null || request.getToAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("to_account is required");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        if (request.getAmount() > 50000) {
            throw new IllegalArgumentException("amount exceeds daily limit of 50,000 MXN");
        }
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new IllegalArgumentException("cannot transfer to same account");
        }
    }

    private Map<String, String> executeTransfer(String fromAccount, String toAccount,
                                                double amount, String description,
                                                Context context) throws Exception {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            context.getLogger().log("DB connection acquired, starting transaction");

            PreparedStatement lockStmt = conn.prepareStatement(
                    "SELECT balance, available_balance, status FROM accounts " +
                            "WHERE account_number = ? FOR UPDATE"
            );
            lockStmt.setString(1, fromAccount);
            ResultSet rs = lockStmt.executeQuery();

            if (!rs.next()) {
                throw new Exception("Sender account not found: " + fromAccount);
            }

            double currentBalance = rs.getDouble("balance");
            double availableBalance = rs.getDouble("available_balance");
            String status = rs.getString("status");

            context.getLogger().log("Sender balance: " + currentBalance + ", status: " + status);

            if (!"ACTIVE".equals(status)) {
                throw new Exception("Account is " + status + ". Cannot process transaction.");
            }

            if (availableBalance < amount) {
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
            deductStmt.setDouble(1, amount);
            deductStmt.setDouble(2, amount);
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
            addStmt.setDouble(1, amount);
            addStmt.setDouble(2, amount);
            addStmt.setString(3, toAccount);
            int updated = addStmt.executeUpdate();

            if (updated == 0) {
                throw new Exception("Recipient account not found: " + toAccount);
            }
            context.getLogger().log("Added " + amount + " to recipient");

            conn.commit();
            context.getLogger().log("Transaction committed successfully");

            double newBalance = currentBalance - amount;
            String transactionId = "txn_" + System.currentTimeMillis() + "_" +
                    fromAccount.substring(fromAccount.length() - 4);

            Map<String, String> result = new HashMap<>();
            result.put("transactionId", transactionId);
            result.put("newBalance", String.valueOf(newBalance));

            return result;

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

    private void publishToQueue(String transactionId, String fromAccount,
                                String toAccount, double amount, String description,
                                Context context) {
        // Check QUEUE_URL before attempting
        if (QUEUE_URL == null || QUEUE_URL.isEmpty()) {
            context.getLogger().log("ERROR: QUEUE_URL environment variable is not set! SQS publish skipped.");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("transaction_id", transactionId);
            message.put("from_account", fromAccount);
            message.put("to_account", toAccount);
            message.put("amount", amount);
            message.put("description", description != null ? description : "");
            message.put("status", "COMPLETED");
            message.put("timestamp", System.currentTimeMillis());
            message.put("type", "TRANSFER");
            message.put("currency", "MXN");

            String messageBody = objectMapper.writeValueAsString(message);
            context.getLogger().log("Publishing to SQS queue: " + QUEUE_URL);
            context.getLogger().log("Message body: " + messageBody);

            SendMessageResponse sqsResponse = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(messageBody)
                    .build()
            );

            context.getLogger().log("SQS message sent successfully! MessageId: " + sqsResponse.messageId());

        } catch (Exception e) {
            // Log the full error — do NOT swallow it silently
            context.getLogger().log("ERROR publishing to SQS: " + e.getMessage());
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