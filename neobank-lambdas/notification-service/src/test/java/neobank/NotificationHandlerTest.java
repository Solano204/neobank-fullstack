package neobank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationHandlerTest {

    private final NotificationHandler handler = new NotificationHandler();

    @Test
    void masksAllButLastFourDigits() {
        assertThat(handler.maskAccountNumber("123456789012345678")).isEqualTo("****5678");
    }

    @Test
    void masksShortOrMissingAccountNumbers() {
        assertThat(handler.maskAccountNumber(null)).isEqualTo("****");
        assertThat(handler.maskAccountNumber("12")).isEqualTo("****");
    }

    @Test
    void buildsSuccessMessageForCompletedTransfers() {
        String message = handler.buildMessage("txn_123", 500.0, "****5678", "COMPLETED");

        assertThat(message).contains("Transfer completed")
                .contains("$500.00 MXN")
                .contains("****5678")
                .contains("txn_123");
    }

    @Test
    void buildsWarningMessageForNonCompletedTransfers() {
        String message = handler.buildMessage("txn_456", 250.0, "****1234", "FAILED");

        assertThat(message).contains("Transaction FAILED")
                .contains("$250.00 MXN")
                .contains("txn_456");
    }
}
