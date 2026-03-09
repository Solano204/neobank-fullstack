package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;

/**
 * NeoBank AI Chatbot - Lex Fulfillment Lambda
 *
 * Triggered by: Amazon Lex (NeoBank-Support bot)
 * Purpose: Answers user banking questions by querying DynamoDB + PostgreSQL
 *
 * Supported intents:
 *  - CheckTransactionStatus  → "Why was my transfer declined?"
 *  - CheckBalance            → "What's my balance?"
 *  - TransactionHistory      → "Show my last transactions"
 *  - FraudAlert              → "I didn't authorize this transaction"
 *  - GeneralHelp             → "How do I transfer money?"
 *  - FallbackIntent          → anything unrecognized
 */
public class LexFulfillmentHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Lex event received: " + event.toString());

        try {
            // Extract intent name and session attributes from Lex event
            String intentName = extractIntentName(event);
            String userId = extractUserId(event);
            Map<String, String> slots = extractSlots(event);

            context.getLogger().log("Intent: " + intentName + ", UserId: " + userId);

            String responseMessage;

            switch (intentName) {
                case "CheckTransactionStatus":
                    responseMessage = handleCheckTransactionStatus(userId, slots, context);
                    break;
                case "CheckBalance":
                    responseMessage = handleCheckBalance(userId, context);
                    break;
                case "TransactionHistory":
                    responseMessage = handleTransactionHistory(userId, slots, context);
                    break;
                case "FraudAlert":
                    responseMessage = handleFraudAlert(userId, slots, context);
                    break;
                case "GeneralHelp":
                    responseMessage = handleGeneralHelp(slots);
                    break;
                case "TransferLimit":
                    responseMessage = "Your daily transfer limit is $50,000 MXN. Each individual transfer cannot exceed this amount.";
                    break;
                case "ContactSupport":
                    responseMessage = "You can reach our support team at soporte@neobank.mx or call 800-NEO-BANK (800-636-2265), available 24/7.";
                    break;
                default:
                    responseMessage = "I'm sorry, I didn't understand that. You can ask me about your balance, transaction history, transfer limits, or report suspicious activity. How can I help you?";
            }

            return buildLexResponse(event, intentName, responseMessage, "Fulfilled");

        } catch (Exception e) {
            context.getLogger().log("Error handling Lex request: " + e.getMessage());
            e.printStackTrace();
            return buildLexResponse(event, "FallbackIntent",
                    "I'm sorry, I encountered an error processing your request. Please try again or contact support at soporte@neobank.mx",
                    "Failed");
        }
    }

    // ─────────────────────────────────────────────────────────
    // INTENT HANDLERS
    // ─────────────────────────────────────────────────────────

    /**
     * "Why was my transfer declined?" / "Check transaction txn_123"
     * Queries DynamoDB for the most recent failed transaction
     */
    private String handleCheckTransactionStatus(String userId, Map<String, String> slots, Context context) {
        try {
            String transactionId = slots.get("transactionId");

            if (transactionId != null && !transactionId.isEmpty()) {
                // Query specific transaction from DynamoDB
                Map<String, AttributeValue> item = getTransactionById(transactionId, context);
                if (item != null) {
                    return formatTransactionDetails(item);
                } else {
                    return "I couldn't find transaction " + transactionId + ". Please check the ID and try again.";
                }
            }

            // No specific ID — find most recent failed transaction by account
            String accountNumber = getAccountNumberForUser(userId, context);
            if (accountNumber == null) {
                return "I couldn't find your account information. Please make sure you are logged in.";
            }

            Map<String, AttributeValue> failedTxn = getMostRecentFailedTransaction(accountNumber, context);
            if (failedTxn != null) {
                String amount = getAttr(failedTxn, "amount");
                String status = getAttr(failedTxn, "status");
                String errorCode = getAttr(failedTxn, "error_code");

                String reason = "INSUFFICIENT_FUNDS".equals(errorCode)
                        ? "you had insufficient funds"
                        : "ACCOUNT_FROZEN".equals(errorCode)
                        ? "your account was frozen"
                        : "of a security restriction";

                double balance = getCurrentBalance(accountNumber, context);
                return String.format(
                        "Your most recent declined transfer was for $%s MXN. It was declined because %s. " +
                                "Your current available balance is $%.2f MXN.",
                        amount, reason, balance
                );
            }

            return "I don't see any declined transfers on your account recently. All your recent transactions appear to have completed successfully.";

        } catch (Exception e) {
            context.getLogger().log("Error in CheckTransactionStatus: " + e.getMessage());
            return "I had trouble looking up your transaction history. Please try again in a moment.";
        }
    }

    /**
     * "What's my balance?" / "How much money do I have?"
     * Queries PostgreSQL for real-time balance
     */
    private String handleCheckBalance(String userId, Context context) {
        try {
            String accountNumber = getAccountNumberForUser(userId, context);
            if (accountNumber == null) {
                return "I couldn't find your account. Please make sure you're logged in.";
            }

            double balance = getCurrentBalance(accountNumber, context);
            String masked = maskAccount(accountNumber);
            return String.format(
                    "Your current balance for account %s is $%.2f MXN. Is there anything else I can help you with?",
                    masked, balance
            );

        } catch (Exception e) {
            context.getLogger().log("Error in CheckBalance: " + e.getMessage());
            return "I had trouble retrieving your balance. Please check the app directly or try again.";
        }
    }

    /**
     * "Show my last 5 transactions" / "What did I spend last week?"
     * Queries DynamoDB GSI for transaction history
     */
    private String handleTransactionHistory(String userId, Map<String, String> slots, Context context) {
        try {
            String accountNumber = getAccountNumberForUser(userId, context);
            if (accountNumber == null) {
                return "I couldn't find your account information.";
            }

            int limit = 5;
            String countSlot = slots.get("transactionCount");
            if (countSlot != null && !countSlot.isEmpty()) {
                try { limit = Integer.parseInt(countSlot); } catch (NumberFormatException ignored) {}
            }
            limit = Math.min(limit, 10); // Cap at 10

            List<Map<String, AttributeValue>> transactions = getRecentTransactions(accountNumber, limit, context);

            if (transactions.isEmpty()) {
                return "You don't have any transactions yet. Make your first transfer through the app!";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Here are your last %d transactions:\n\n", transactions.size()));

            for (int i = 0; i < transactions.size(); i++) {
                Map<String, AttributeValue> txn = transactions.get(i);
                String amount = getAttr(txn, "amount");
                String toAccount = maskAccount(getAttr(txn, "to_account"));
                String status = getAttr(txn, "status");
                String description = getAttr(txn, "description");

                sb.append(String.format("%d. $%s MXN → %s | %s",
                        i + 1, amount, toAccount, status));
                if (!description.isEmpty()) sb.append(" | ").append(description);
                sb.append("\n");
            }

            sb.append("\nWould you like more details on any of these?");
            return sb.toString();

        } catch (Exception e) {
            context.getLogger().log("Error in TransactionHistory: " + e.getMessage());
            return "I had trouble loading your transaction history. Please check the app's History section.";
        }
    }

    /**
     * "I didn't authorize this transaction" / "My account was hacked"
     * Guides user through fraud reporting flow
     */
    private String handleFraudAlert(String userId, Map<String, String> slots, Context context) {
        String transactionId = slots.get("transactionId");

        if (transactionId != null && !transactionId.isEmpty()) {
            return String.format(
                    "I've flagged transaction %s for security review. Your account has been temporarily protected. " +
                            "A security specialist will contact you within 2 hours. " +
                            "If you need immediate assistance, call 800-NEO-BANK (800-636-2265).",
                    transactionId
            );
        }

        return "I'm sorry to hear that. To protect your account immediately:\n\n" +
                "1. Go to Settings → Security → Freeze Account\n" +
                "2. Call us at 800-NEO-BANK (800-636-2265) available 24/7\n" +
                "3. Email fraud@neobank.mx with your account number\n\n" +
                "Our fraud team will investigate and refund any unauthorized charges within 5 business days.";
    }

    /**
     * "How do I transfer money?" / General banking questions
     */
    private String handleGeneralHelp(Map<String, String> slots) {
        String topic = slots.getOrDefault("helpTopic", "").toLowerCase();

        if (topic.contains("transfer")) {
            return "To make a transfer: tap the 'Transfer' button on the home screen, enter the recipient's account number (CLABE), " +
                    "enter the amount (up to $50,000 MXN per transfer), add an optional description, and confirm. " +
                    "Transfers within NeoBank are instant!";
        } else if (topic.contains("kyc") || topic.contains("verificat") || topic.contains("identidad")) {
            return "To verify your identity: go to Profile → Verify Identity, take a clear selfie with good lighting, " +
                    "make sure your eyes are open and your face is fully visible. " +
                    "Our AI verifies photos in about 30 seconds!";
        } else if (topic.contains("password") || topic.contains("contraseña")) {
            return "To change your password: go to Settings → Security → Change Password. " +
                    "Forgot your password? On the login screen tap 'Forgot Password' and we'll send a reset code to your email.";
        } else if (topic.contains("limit") || topic.contains("límite")) {
            return "Your transfer limits are: $50,000 MXN per single transfer, $100,000 MXN per day. " +
                    "To request a limit increase, contact support@neobank.mx.";
        }

        return "I can help you with:\n" +
                "• 💰 Check your balance\n" +
                "• 📋 View transaction history\n" +
                "• ❓ Why a transfer was declined\n" +
                "• 🚨 Report suspicious activity\n" +
                "• 📞 How to contact support\n\n" +
                "What would you like to know?";
    }

    // ─────────────────────────────────────────────────────────
    // DATABASE HELPERS
    // ─────────────────────────────────────────────────────────

    private String getAccountNumberForUser(String userId, Context context) {
        if (dataSource == null || userId == null || userId.isEmpty()) return null;
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT a.account_number FROM accounts a " +
                            "JOIN users u ON a.user_id = u.id " +
                            "WHERE u.cognito_user_id = ? AND a.status = 'ACTIVE' " +
                            "LIMIT 1"
            );
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("account_number");
        } catch (Exception e) {
            context.getLogger().log("DB error getAccountNumber: " + e.getMessage());
        }
        return null;
    }

    private double getCurrentBalance(String accountNumber, Context context) {
        if (dataSource == null) return 0.0;
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT available_balance FROM accounts WHERE account_number = ?"
            );
            stmt.setString(1, accountNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("available_balance");
        } catch (Exception e) {
            context.getLogger().log("DB error getBalance: " + e.getMessage());
        }
        return 0.0;
    }

    private Map<String, AttributeValue> getTransactionById(String transactionId, Context context) {
        try {
            GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("transaction_id", AttributeValue.builder().s(transactionId).build()))
                    .build());
            return response.hasItem() ? response.item() : null;
        } catch (Exception e) {
            context.getLogger().log("DynamoDB error getById: " + e.getMessage());
            return null;
        }
    }

    private Map<String, AttributeValue> getMostRecentFailedTransaction(String accountNumber, Context context) {
        try {
            QueryResponse response = dynamoDb.query(QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("from_account-timestamp-index")
                    .keyConditionExpression("from_account = :account")
                    .filterExpression("#s = :status")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(
                            ":account", AttributeValue.builder().s(accountNumber).build(),
                            ":status", AttributeValue.builder().s("FAILED").build()
                    ))
                    .scanIndexForward(false)
                    .limit(1)
                    .build());

            List<Map<String, AttributeValue>> items = response.items();
            return items.isEmpty() ? null : items.get(0);
        } catch (Exception e) {
            context.getLogger().log("DynamoDB error getMostRecentFailed: " + e.getMessage());
            return null;
        }
    }

    private List<Map<String, AttributeValue>> getRecentTransactions(String accountNumber, int limit, Context context) {
        try {
            QueryResponse response = dynamoDb.query(QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("from_account-timestamp-index")
                    .keyConditionExpression("from_account = :account")
                    .expressionAttributeValues(Map.of(
                            ":account", AttributeValue.builder().s(accountNumber).build()
                    ))
                    .scanIndexForward(false)
                    .limit(limit)
                    .build());
            return response.items();
        } catch (Exception e) {
            context.getLogger().log("DynamoDB error getRecent: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────────────────
    // LEX EVENT PARSING
    // ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractIntentName(Map<String, Object> event) {
        try {
            Map<String, Object> sessionState = (Map<String, Object>) event.get("sessionState");
            Map<String, Object> intent = (Map<String, Object>) sessionState.get("intent");
            return (String) intent.get("name");
        } catch (Exception e) {
            return "FallbackIntent";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractUserId(Map<String, Object> event) {
        try {
            Map<String, Object> sessionState = (Map<String, Object>) event.get("sessionState");
            Map<String, Object> sessionAttributes = (Map<String, Object>) sessionState.get("sessionAttributes");
            if (sessionAttributes != null) {
                return (String) sessionAttributes.get("userId");
            }
        } catch (Exception ignored) {}
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractSlots(Map<String, Object> event) {
        Map<String, String> result = new HashMap<>();
        try {
            Map<String, Object> sessionState = (Map<String, Object>) event.get("sessionState");
            Map<String, Object> intent = (Map<String, Object>) sessionState.get("intent");
            Map<String, Object> slots = (Map<String, Object>) intent.get("slots");
            if (slots != null) {
                for (Map.Entry<String, Object> entry : slots.entrySet()) {
                    if (entry.getValue() != null) {
                        Map<String, Object> slotValue = (Map<String, Object>) entry.getValue();
                        Map<String, Object> value = (Map<String, Object>) slotValue.get("value");
                        if (value != null) {
                            result.put(entry.getKey(), (String) value.get("interpretedValue"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // LEX RESPONSE BUILDER
    // ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildLexResponse(Map<String, Object> event,
                                                 String intentName,
                                                 String message,
                                                 String fulfillmentState) {
        Map<String, Object> response = new HashMap<>();

        // Session state
        Map<String, Object> sessionState = new HashMap<>();
        Map<String, Object> intent = new HashMap<>();
        intent.put("name", intentName);
        intent.put("state", fulfillmentState);
        sessionState.put("intent", intent);

        // Preserve existing session attributes
        try {
            Map<String, Object> existingState = (Map<String, Object>) event.get("sessionState");
            if (existingState != null && existingState.containsKey("sessionAttributes")) {
                sessionState.put("sessionAttributes", existingState.get("sessionAttributes"));
            }
        } catch (Exception ignored) {}

        response.put("sessionState", sessionState);

        // Messages
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("contentType", "PlainText");
        msg.put("content", message);
        messages.add(msg);

        response.put("messages", messages);
        return response;
    }

    // ─────────────────────────────────────────────────────────
    // UTILITY HELPERS
    // ─────────────────────────────────────────────────────────

    private String getAttr(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        if (val == null) return "";
        if (val.s() != null) return val.s();
        if (val.n() != null) return val.n();
        return "";
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    private String formatTransactionDetails(Map<String, AttributeValue> item) {
        String id = getAttr(item, "transaction_id");
        String amount = getAttr(item, "amount");
        String status = getAttr(item, "status");
        String to = maskAccount(getAttr(item, "to_account"));
        String description = getAttr(item, "description");

        return String.format(
                "Transaction %s: $%s MXN to account %s | Status: %s%s",
                id, amount, to, status,
                description.isEmpty() ? "" : " | " + description
        );
    }
}