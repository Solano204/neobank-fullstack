package neobank.infrastructure.exception;


import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
public class InsufficientFundsException extends RuntimeException {

    private final Map<String, Object> data;

    public InsufficientFundsException(BigDecimal currentBalance, BigDecimal requiredAmount) {
        super(String.format("Balance: %s, Required: %s", currentBalance, requiredAmount));

        this.data = new HashMap<>();
        this.data.put("currentBalance", currentBalance);
        this.data.put("requiredAmount", requiredAmount);
    }
}