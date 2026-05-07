package cm.ict304.payment.exception;

public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String accountNumber) {
        super("Compte suspendu ou fermé : " + accountNumber);
    }
}
