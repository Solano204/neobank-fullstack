package neobank;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LexFulfillmentHandlerTest {

    private final LexFulfillmentHandler handler = new LexFulfillmentHandler();

    private Map<String, Object> lexEvent(String intentName, String userId, Map<String, String> slotValues) {
        Map<String, Object> slots = new HashMap<>();
        slotValues.forEach((key, value) -> slots.put(key, Map.of("value", Map.of("interpretedValue", value))));

        Map<String, Object> intent = new HashMap<>();
        intent.put("name", intentName);
        intent.put("slots", slots);

        Map<String, Object> sessionState = new HashMap<>();
        sessionState.put("intent", intent);
        sessionState.put("sessionAttributes", Map.of("userId", userId));

        Map<String, Object> event = new HashMap<>();
        event.put("sessionState", sessionState);
        return event;
    }

    @Test
    void extractsIntentNameFromLexEvent() {
        Map<String, Object> event = lexEvent("CheckBalance", "user-1", Map.of());

        assertThat(handler.extractIntentName(event)).isEqualTo("CheckBalance");
    }

    @Test
    void fallsBackToFallbackIntentForMalformedEvent() {
        assertThat(handler.extractIntentName(Map.of())).isEqualTo("FallbackIntent");
    }

    @Test
    void extractsUserIdFromSessionAttributes() {
        Map<String, Object> event = lexEvent("CheckBalance", "user-42", Map.of());

        assertThat(handler.extractUserId(event)).isEqualTo("user-42");
    }

    @Test
    void extractsUserIdAsEmptyStringWhenMissing() {
        assertThat(handler.extractUserId(Map.of())).isEmpty();
    }

    @Test
    void extractsSlotInterpretedValues() {
        Map<String, Object> event = lexEvent("TransactionHistory", "user-1", Map.of("transactionCount", "5"));

        assertThat(handler.extractSlots(event)).containsEntry("transactionCount", "5");
    }

    @Test
    void buildsLexResponsePreservingSessionAttributes() {
        Map<String, Object> event = lexEvent("CheckBalance", "user-1", Map.of());

        Map<String, Object> response = handler.buildLexResponse(event, "CheckBalance", "Your balance is $100", "Fulfilled");

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionState = (Map<String, Object>) response.get("sessionState");
        assertThat(sessionState).containsKey("sessionAttributes");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("content", "Your balance is $100");
    }

    @Test
    void masksAllButLastFourDigitsOfAccountNumber() {
        assertThat(handler.maskAccount("123456789012345678")).isEqualTo("****5678");
    }

    @Test
    void masksNullOrShortAccountNumbers() {
        assertThat(handler.maskAccount(null)).isEqualTo("****");
        assertThat(handler.maskAccount("12")).isEqualTo("****");
    }

    @Test
    void readsStringAttributeFromDynamoItem() {
        Map<String, AttributeValue> item = Map.of("status", AttributeValue.builder().s("COMPLETED").build());

        assertThat(handler.getAttr(item, "status")).isEqualTo("COMPLETED");
    }

    @Test
    void readsNumericAttributeFromDynamoItem() {
        Map<String, AttributeValue> item = Map.of("amount", AttributeValue.builder().n("250.50").build());

        assertThat(handler.getAttr(item, "amount")).isEqualTo("250.50");
    }

    @Test
    void formatsTransactionDetailsWithMaskedRecipient() {
        Map<String, AttributeValue> item = Map.of(
                "transaction_id", AttributeValue.builder().s("txn_1").build(),
                "amount", AttributeValue.builder().n("100").build(),
                "status", AttributeValue.builder().s("COMPLETED").build(),
                "to_account", AttributeValue.builder().s("123456789012345678").build(),
                "description", AttributeValue.builder().s("").build()
        );

        assertThat(handler.formatTransactionDetails(item))
                .contains("txn_1")
                .contains("$100 MXN")
                .contains("****5678")
                .contains("COMPLETED");
    }

    @Test
    void generalHelpExplainsTransfersWhenTopicMatches() {
        assertThat(handler.handleGeneralHelp(Map.of("helpTopic", "transfer")))
                .contains("Transfer");
    }

    @Test
    void generalHelpFallsBackToMenuForUnknownTopic() {
        assertThat(handler.handleGeneralHelp(Map.of()))
                .contains("I can help you with");
    }

    @Test
    void fraudAlertReferencesTheTransactionWhenProvided() {
        assertThat(handler.handleFraudAlert("user-1", Map.of("transactionId", "txn_99"), null))
                .contains("txn_99")
                .contains("flagged");
    }

    @Test
    void fraudAlertGivesGeneralGuidanceWithoutATransactionId() {
        assertThat(handler.handleFraudAlert("user-1", Map.of(), null))
                .contains("Freeze Account");
    }
}
