package neobank;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionQueryHandlerTest {

    private final TransactionQueryHandler handler = new TransactionQueryHandler();

    @Test
    void readsStringValueWhenPresent() {
        Map<String, AttributeValue> item = Map.of(
                "status", AttributeValue.builder().s("COMPLETED").build());

        assertThat(handler.getStringValue(item, "status")).isEqualTo("COMPLETED");
    }

    @Test
    void returnsEmptyStringWhenAttributeMissing() {
        assertThat(handler.getStringValue(Map.of(), "status")).isEmpty();
    }

    @Test
    void parsesLongValue() {
        Map<String, AttributeValue> item = Map.of(
                "timestamp", AttributeValue.builder().n("1700000000000").build());

        assertThat(handler.getLongValue(item, "timestamp")).isEqualTo(1700000000000L);
    }

    @Test
    void returnsZeroLongWhenAttributeMissing() {
        assertThat(handler.getLongValue(Map.of(), "timestamp")).isZero();
    }

    // getDoubleValue was deliberately replaced with getBigDecimalValue (see
    // the comment at its call site in queryByIndex) to avoid exactly the
    // float round-trip precision drift these tests exist to catch - a
    // double can't represent every decimal amount exactly, so asserting via
    // BigDecimal is the whole point, not an incidental type change.
    @Test
    void parsesBigDecimalValueExactly() {
        Map<String, AttributeValue> item = Map.of(
                "amount", AttributeValue.builder().n("1234.56").build());

        assertThat(handler.getBigDecimalValue(item, "amount")).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void parsesABigDecimalValueThatWouldLoseFractionalPrecisionAsADouble() {
        Map<String, AttributeValue> item = Map.of(
                "amount", AttributeValue.builder().n("100000000000000.11").build());

        assertThat(handler.getBigDecimalValue(item, "amount"))
                .isEqualByComparingTo(new BigDecimal("100000000000000.11"));
    }

    @Test
    void returnsZeroBigDecimalWhenAttributeMissing() {
        assertThat(handler.getBigDecimalValue(Map.of(), "amount")).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
