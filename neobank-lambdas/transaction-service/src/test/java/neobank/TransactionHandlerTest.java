package neobank;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionHandlerTest {

    private final TransactionHandler handler = new TransactionHandler();

    private TransactionRequest request(String from, String to, String amount) {
        TransactionRequest request = new TransactionRequest();
        request.setFromAccount(from);
        request.setToAccount(to);
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        return request;
    }

    @Test
    void acceptsAValidTransfer() {
        assertThatCode(() -> handler.validateRequest(request("111111111111111111", "222222222222222222", "100.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingFromAccount() {
        assertThatThrownBy(() -> handler.validateRequest(request(null, "222222222222222222", "100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from_account is required");
    }

    @Test
    void rejectsMissingToAccount() {
        assertThatThrownBy(() -> handler.validateRequest(request("111111111111111111", "  ", "100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to_account is required");
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> handler.validateRequest(request("111111111111111111", "222222222222222222", "0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than 0");
    }

    @Test
    void rejectsAmountOverTheDailyLimit() {
        assertThatThrownBy(() -> handler.validateRequest(request("111111111111111111", "222222222222222222", "50000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds daily limit");
    }

    @Test
    void acceptsAmountExactlyAtTheDailyLimit() {
        assertThatCode(() -> handler.validateRequest(request("111111111111111111", "222222222222222222", "50000.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        assertThatThrownBy(() -> handler.validateRequest(request("111111111111111111", "111111111111111111", "100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot transfer to same account");
    }
}
