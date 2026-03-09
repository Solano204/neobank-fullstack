package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

public class NotificationHandler implements RequestHandler<SQSEvent, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SnsClient snsClient = SnsClient.create();
    private static final String TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");

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

                sendNotification(transaction, context);
                successCount++;

                context.getLogger().log("Notification sent for: " + transaction.get("transaction_id"));

            } catch (Exception e) {
                failureCount++;
                context.getLogger().log("Error sending notification: " + e.getMessage());
                e.printStackTrace();
            }
        }

        context.getLogger().log(String.format(
                "Notifications sent. Success: %d, Failures: %d",
                successCount, failureCount
        ));

        return "SUCCESS";
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

    private String buildMessage(String transactionId, double amount,
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

    private String maskAccountNumber(String accountNumber) {
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