package cm.ict304.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un compte bancaire.
 * Champ clé pour les tests de flux de données : balance
 */
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @NotBlank
    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.accountNumber == null) {
            // Format : PAY-XXXXXXXX (8 hex chars)
            this.accountNumber = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.status == null) {
            this.status = AccountStatus.ACTIVE;
        }
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
    }

    public Account(String ownerName, BigDecimal initialBalance) {
        this.ownerName = ownerName;
        this.balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
    }

    /**
     * Vérifie si le solde est suffisant pour un retrait.
     * Méthode clé pour les tests unitaires boîte blanche.
     *
     * @param amount montant à retirer
     * @return true si le solde est suffisant
     */
    public boolean hasSufficientFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.balance.compareTo(amount) >= 0;
    }

    /**
     * Crédite le compte du montant donné.
     *
     * @param amount montant à ajouter (doit être > 0)
     */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant à créditer doit être positif");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Débite le compte du montant donné.
     *
     * @param amount montant à retirer (doit être > 0 et <= solde)
     */
    public void debit(BigDecimal amount) {
        if (!hasSufficientFunds(amount)) {
            throw new IllegalArgumentException("Fonds insuffisants pour le débit");
        }
        this.balance = this.balance.subtract(amount);
    }
}
