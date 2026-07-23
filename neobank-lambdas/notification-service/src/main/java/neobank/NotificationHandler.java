package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SnsClient snsClient = SnsClient.create();
    private static final String TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");
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
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                Map<String, Object> transaction = objectMapper.readValue(
                        message.getBody(),
                        Map.class
                );

                sendNotification(transaction, context);
                persistNotifications(transaction, context);

                context.getLogger().log("Notification sent for: " + transaction.get("transaction_id"));

            } catch (Exception e) {
                context.getLogger().log("Error sending notification: " + e.getMessage());
                e.printStackTrace();
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        context.getLogger().log(String.format(
                "Notifications sent. Success: %d, Failures: %d",
                event.getRecords().size() - failures.size(), failures.size()
        ));

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private void sendNotification(Map<String, Object> transaction, Context context) {
        String transactionId = getString(transaction, "transaction_id");
        double amount = getDouble(transaction, "amount");
        String toAccount = getString(transaction, "to_account");
        String status = getString(transaction, "status");

        String maskedAccount = maskAccountNumber(toAccount);

        String notificationMessage = buildMessage(transactionId, amount, maskedAccount, status);

        try {
            PublishRequest request = PublishRequest.builder()
                    .topicArn(TOPIC_ARN)
                    .subject("Transaction Notification")
                    .message(notificationMessage)
                    .build();

            snsClient.publish(request);

            context.getLogger().log("SNS message published successfully");

        } catch (Exception e) {
            context.getLogger().log("Failed to publish to SNS: " + e.getMessage());
            throw e;
        }
    }

    /**
     * The SNS publish above is fire-and-forget (email/SMS/push) — nothing the
     * app itself can show the user. This writes the same event to the
     * notifications table so GET /api/notifications on the Spring backend has
     * something real to return instead of an always-empty stub.
     */
    private void persistNotifications(Map<String, Object> transaction, Context context) {
        if (dataSource == null) return;

        String transactionId = getString(transaction, "transaction_id");
        double amount = getDouble(transaction, "amount");
        String fromAccount = getString(transaction, "from_account");
        String toAccount = getString(transaction, "to_account");
        String status = getString(transaction, "status");

        boolean completed = "COMPLETED".equals(status);
        String recipientTitle = completed ? "Money received" : "Transaction " + status;
        String recipientMessage = String.format("You received $%.2f MXN from %s. Transaction %s.",
                amount, maskAccountNumber(fromAccount), transactionId);

        String senderTitle = completed ? "Transfer sent" : "Transfer " + status;
        String senderMessage = String.format("You sent $%.2f MXN to %s. Transaction %s.",
                amount, maskAccountNumber(toAccount), transactionId);

        try (Connection conn = dataSource.getConnection()) {
            insertForAccountOwner(conn, toAccount, recipientTitle, recipientMessage, context);
            insertForAccountOwner(conn, fromAccount, senderTitle, senderMessage, context);
        } catch (Exception e) {
            context.getLogger().log("Failed to persist notification: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void insertForAccountOwner(Connection conn, String accountNumber, String title,
                                        String message, Context context) throws Exception {
        String userId = findUserIdForAccount(conn, accountNumber);
        if (userId == null) {
            context.getLogger().log("No user found for account " + accountNumber + "; skipping notification row");
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO notifications (user_id, title, message, type, read) VALUES (?::uuid, ?, ?, 'TRANSACTION', false)")) {
            stmt.setString(1, userId);
            stmt.setString(2, title);
            stmt.setString(3, message);
            stmt.executeUpdate();
        }
    }

    private String findUserIdForAccount(Connection conn, String accountNumber) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id FROM accounts WHERE account_number = ?")) {
            stmt.setString(1, accountNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("user_id") : null;
            }
        }
    }

    String buildMessage(String transactionId, double amount,
                                String maskedAccount, String status) {
        if ("COMPLETED".equals(status)) {
            return String.format(
                    "✓ Transfer completed\n" +
                            "Amount: $%.2f MXN\n" +
                            "To: %s\n" +
                            "Transaction ID: %s",
                    amount, maskedAccount, transactionId
            );
        } else {
            return String.format(
                    "⚠ Transaction %s\n" +
                            "Amount: $%.2f MXN\n" +
                            "Status: %s\n" +
                            "Transaction ID: %s",
                    status, amount, status, transactionId
            );
        }
    }

    String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
