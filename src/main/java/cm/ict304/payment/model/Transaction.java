package cm.ict304.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant une transaction financière.
 * Une transaction est IMMUABLE après création (audit trail).
 */
@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence unique de la transaction */
    @Column(unique = true, nullable = false)
    private String reference;

    /** Compte source (null pour les dépôts externes) */
    @Column
    private String fromAccountNumber;

    /** Compte destination (null pour les retraits externes) */
    @Column
    private String toAccountNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        this.executedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = TransactionStatus.SUCCESS;
        }
        if (this.reference == null) {
            this.reference = "TXN-" + System.currentTimeMillis();
        }
    }

    public Transaction(String fromAccount, String toAccount,
                       BigDecimal amount, TransactionType type, String description) {
        this.fromAccountNumber = fromAccount;
        this.toAccountNumber = toAccount;
        this.amount = amount;
        this.type = type;
        this.description = description;
    }
}
