package neobank;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class TransactionResponse {

    private String status;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("from_account")
    private String fromAccount;

    @JsonProperty("to_account")
    private String toAccount;

    private BigDecimal amount;

    @JsonProperty("new_balance")
    private BigDecimal newBalance;

    private Long timestamp;

    public TransactionResponse() {}

    public TransactionResponse(String status, String transactionId, String fromAccount,
                               String toAccount, BigDecimal amount, BigDecimal newBalance) {
        this.status = status;
        this.transactionId = transactionId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.newBalance = newBalance;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getNewBalance() { return newBalance; }
    public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}