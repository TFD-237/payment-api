package cm.ict304.payment.service;

import cm.ict304.payment.dto.AccountCreateRequest;
import cm.ict304.payment.exception.AccountNotFoundException;
import cm.ict304.payment.exception.AccountSuspendedException;
import cm.ict304.payment.exception.InsufficientFundsException;
import cm.ict304.payment.model.*;
import cm.ict304.payment.repository.AccountRepository;
import cm.ict304.payment.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * SERVICE MÉTIER : AccountService
 * ─────────────────────────────────────────────────────────────────────────────
 * Contient toute la logique métier de l'API de paiement.
 * C'est LA classe la plus importante pour les tests ICT304 :
 *
 *  • Tests boîte blanche (CFG) → chaque méthode a un graphe de flux de contrôle
 *  • Tests de flux de données (DFT) → variables : account, amount, balance
 *  • Tests de mutation (PIT) → les opérateurs arithmétiques et les conditions
 *  • Tests unitaires JUnit → 1 test par chemin logique de chaque méthode
 *
 * Toutes les méthodes sont @Transactional : soit tout réussit, soit rien n'est sauvegardé.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CRÉATION DE COMPTE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Crée un nouveau compte bancaire.
     *
     * CFG (Graphe de Flux de Contrôle) :
     *   [START] → [valider ownerName] → [créer Account] → [sauvegarder] → [END]
     *   Chemin unique → couverture C1 = 1 cas de test
     *
     * @param request données de création (nom + solde initial)
     * @return le compte créé (avec accountNumber généré)
     */
    public Account createAccount(AccountCreateRequest request) {
        Account account = new Account(
                request.getOwnerName(),
                request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO
        );
        return accountRepository.save(account);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DÉPÔT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Effectue un dépôt sur un compte.
     *
     * CFG (3 chemins — important pour les tests boîte blanche) :
     *
     *   [START]
     *      │
     *      ▼
     *   [Trouver le compte par numéro]
     *      │                    │
     *      │ trouvé             │ non trouvé
     *      ▼                    ▼
     *   [Vérifier statut]   [lever AccountNotFoundException] → [END-ERR1]
     *      │             │
     *      │ ACTIVE       │ SUSPENDED/CLOSED
     *      ▼             ▼
     *   [Créditer]   [lever AccountSuspendedException] → [END-ERR2]
     *      │
     *      ▼
     *   [Enregistrer Transaction]
     *      │
     *      ▼
     *   [END-OK]
     *
     * Couverture C2 (branches) = 3 cas de test :
     *   CT1 : compte trouvé + ACTIVE → dépôt OK
     *   CT2 : compte non trouvé → AccountNotFoundException
     *   CT3 : compte SUSPENDED → AccountSuspendedException
     *
     * @param accountNumber numéro du compte
     * @param amount montant à déposer (doit être > 0)
     * @param description libellé de la transaction
     * @return la Transaction créée
     */
    public Transaction deposit(String accountNumber, BigDecimal amount, String description) {
        // DEF(account) : définition de la variable account
        Account account = findActiveAccount(accountNumber);

        // USE(account) : utilisation pour créditer
        account.credit(amount);
        accountRepository.save(account);

        // Enregistrement de la transaction
        Transaction tx = new Transaction(null, accountNumber, amount, TransactionType.DEPOSIT, description);
        return transactionRepository.save(tx);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RETRAIT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Effectue un retrait sur un compte.
     *
     * CFG (4 chemins — le plus complexe → bon sujet de test de mutation) :
     *
     *   [START]
     *      │
     *      ▼
     *   [Trouver le compte] → [non trouvé] → AccountNotFoundException → [END]
     *      │
     *      ▼
     *   [Vérifier statut] → [suspendu] → AccountSuspendedException → [END]
     *      │
     *      ▼
     *   [Vérifier solde] ─── account.balance >= amount ? ───
     *      │ OUI                                              │ NON
     *      ▼                                                  ▼
     *   [Débiter]                              [lever InsufficientFundsException] → [END]
     *      │
     *      ▼
     *   [Enregistrer Transaction] → [END-OK]
     *
     * Couverture C2 = 4 cas de test :
     *   CT1 : compte trouvé + ACTIVE + solde suffisant → retrait OK
     *   CT2 : compte non trouvé → AccountNotFoundException
     *   CT3 : compte SUSPENDED → AccountSuspendedException
     *   CT4 : solde insuffisant → InsufficientFundsException
     *
     * Tests de mutation cibles :
     *   • Changer >= en > dans hasSufficientFunds → doit tuer le mutant
     *   • Changer subtract en add → doit tuer le mutant
     *
     * @param accountNumber numéro du compte
     * @param amount montant à retirer
     * @param description libellé
     * @return la Transaction créée
     */
    public Transaction withdraw(String accountNumber, BigDecimal amount, String description) {
        Account account = findActiveAccount(accountNumber);

        // DEF(balance) via USE(account)
        if (!account.hasSufficientFunds(amount)) {
            // USE(balance) dans la condition — All-P-Uses
            throw new InsufficientFundsException(accountNumber, account.getBalance(), amount);
        }

        // USE(account) pour débiter — All-C-Uses
        account.debit(amount);
        accountRepository.save(account);

        Transaction tx = new Transaction(accountNumber, null, amount, TransactionType.WITHDRAWAL, description);
        return transactionRepository.save(tx);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSFERT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Effectue un transfert entre deux comptes.
     *
     * CFG (5 chemins possibles) :
     *   [Vérifier source ≠ destination]
     *   [Trouver source] → [non trouvé] → Exception
     *   [Trouver destination] → [non trouvé] → Exception
     *   [Vérifier solde source] → [insuffisant] → Exception
     *   [Débiter source + Créditer destination] → [Enregistrer] → OK
     *
     * @param fromAccountNumber compte source
     * @param toAccountNumber compte destination
     * @param amount montant à transférer
     * @param description libellé
     * @return la Transaction créée
     */
    public Transaction transfer(String fromAccountNumber, String toAccountNumber,
                                BigDecimal amount, String description) {
        // Validation métier : on ne peut pas transférer vers soi-même
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Le compte source et destination ne peuvent pas être identiques");
        }

        // DEF(source) et DEF(destination)
        Account source = findActiveAccount(fromAccountNumber);
        Account destination = findActiveAccount(toAccountNumber);

        // USE(source.balance) dans la condition — All-P-Uses
        if (!source.hasSufficientFunds(amount)) {
            throw new InsufficientFundsException(fromAccountNumber, source.getBalance(), amount);
        }

        // USE(source) et USE(destination) — All-C-Uses
        source.debit(amount);
        destination.credit(amount);

        accountRepository.save(source);
        accountRepository.save(destination);

        Transaction tx = new Transaction(fromAccountNumber, toAccountNumber,
                amount, TransactionType.TRANSFER, description);
        return transactionRepository.save(tx);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REQUÊTES (lecture seule)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(String accountNumber) {
        // Vérifier que le compte existe avant de chercher les transactions
        getAccount(accountNumber);
        return transactionRepository.findAllByAccountNumber(accountNumber);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountNumber) {
        return getAccount(accountNumber).getBalance();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MÉTHODE UTILITAIRE PRIVÉE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Trouve un compte ACTIF. Lance des exceptions si introuvable ou suspendu.
     * Méthode réutilisée par deposit, withdraw, transfer.
     *
     * Chaîne DU (flux de données) :
     *   DEF(account) ici → USE(account) dans les méthodes appelantes
     */
    private Account findActiveAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException(accountNumber);
        }

        return account;
    }
}
