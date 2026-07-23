package neobank;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsHandlerTest {

    private final AnalyticsHandler handler = new AnalyticsHandler();

    @Test
    void readsStringFieldWhenPresent() {
        assertThat(handler.getString(Map.of("type", "TRANSFER"), "type")).isEqualTo("TRANSFER");
    }

    @Test
    void returnsDefaultWhenFieldMissing() {
        assertThat(handler.getString(new HashMap<>(), "type", "TRANSFER")).isEqualTo("TRANSFER");
    }

    @Test
    void parsesDoubleFromNumber() {
        assertThat(handler.getDouble(Map.of("amount", 42.5), "amount")).isEqualTo(42.5);
    }

    @Test
    void returnsZeroWhenAmountMissing() {
        assertThat(handler.getDouble(new HashMap<>(), "amount")).isZero();
    }

    @Test
    void averageTransactionAmountEchoesCurrentAmount() {
        assertThat(handler.calculateAverageTransactionAmount(123.45)).isEqualTo(123.45);
    }
}
