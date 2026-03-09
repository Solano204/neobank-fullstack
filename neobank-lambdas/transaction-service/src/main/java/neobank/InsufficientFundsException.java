package neobank;


public class InsufficientFundsException extends Exception {

    private final double currentBalance;
    private final double requiredAmount;

    public InsufficientFundsException(double currentBalance, double requiredAmount) {
        super(String.format("Insufficient funds. Balance: %.2f, Required: %.2f",
                currentBalance, requiredAmount));
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public double getCurrentBalance() { return currentBalance; }
    public double getRequiredAmount() { return requiredAmount; }
}