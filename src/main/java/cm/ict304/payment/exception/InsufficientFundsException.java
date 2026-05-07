package cm.ict304.payment.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountNumber, java.math.BigDecimal available, java.math.BigDecimal requested) {
        super(String.format("Fonds insuffisants sur %s. Disponible : %.2f, Demandé : %.2f",
                accountNumber, available, requested));
    }
}
