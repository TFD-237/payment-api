package cm.ict304.payment.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountNumber) {
        super("Compte introuvable : " + accountNumber);
    }
}
