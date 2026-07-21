package neobank;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerWriterHandlerTest {

    private final LedgerWriterHandler handler = new LedgerWriterHandler();

    @Test
    void readsStringFieldWhenPresent() {
        Map<String, Object> transaction = Map.of("status", "COMPLETED");

        assertThat(handler.getString(transaction, "status")).isEqualTo("COMPLETED");
    }

    @Test
    void returnsEmptyStringWhenFieldMissing() {
        assertThat(handler.getString(new HashMap<>(), "status")).isEmpty();
    }

    @Test
    void returnsDefaultValueWhenFieldMissing() {
        assertThat(handler.getString(new HashMap<>(), "currency", "MXN")).isEqualTo("MXN");
    }

    @Test
    void keepsActualValueOverDefaultWhenFieldPresent() {
        Map<String, Object> transaction = Map.of("currency", "USD");

        assertThat(handler.getString(transaction, "currency", "MXN")).isEqualTo("USD");
    }

    @Test
    void parsesLongFromNumber() {
        Map<String, Object> transaction = Map.of("timestamp", 1700000000000L);

        assertThat(handler.getLong(transaction, "timestamp")).isEqualTo(1700000000000L);
    }

    @Test
    void returnsZeroLongWhenFieldNotANumber() {
        Map<String, Object> transaction = Map.of("timestamp", "not-a-number");

        assertThat(handler.getLong(transaction, "timestamp")).isZero();
    }

    @Test
    void parsesDoubleFromNumber() {
        Map<String, Object> transaction = Map.of("amount", 500.25);

        assertThat(handler.getDouble(transaction, "amount")).isEqualTo(500.25);
    }

    @Test
    void returnsZeroDoubleWhenFieldMissing() {
        assertThat(handler.getDouble(new HashMap<>(), "amount")).isZero();
    }
}
