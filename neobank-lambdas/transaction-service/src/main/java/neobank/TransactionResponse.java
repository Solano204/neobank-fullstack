package neobank;


import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionResponse {

    private String status;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("from_account")
    private String fromAccount;

    @JsonProperty("to_account")
    private String toAccount;

    private Double amount;

    @JsonProperty("new_balance")
    private Double newBalance;

    private Long timestamp;

    public TransactionResponse() {}

    public TransactionResponse(String status, String transactionId, String fromAccount,
                               String toAccount, Double amount, Double newBalance) {
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

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getNewBalance() { return newBalance; }
    public void setNewBalance(Double newBalance) { this.newBalance = newBalance; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}