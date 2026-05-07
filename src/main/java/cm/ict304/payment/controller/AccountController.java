package cm.ict304.payment.controller;

import cm.ict304.payment.dto.AccountCreateRequest;
import cm.ict304.payment.dto.DepositRequest;
import cm.ict304.payment.dto.TransferRequest;
import cm.ict304.payment.dto.WithdrawRequest;
import cm.ict304.payment.model.Account;
import cm.ict304.payment.model.Transaction;
import cm.ict304.payment.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CONTRÔLEUR REST : AccountController
 * ─────────────────────────────────────────────────────────────────────────────
 * Expose les endpoints HTTP de l'API de paiement.
 *
 * Base URL : /api/v1/accounts
 *
 * Endpoints :
 *   POST   /              → créer un compte
 *   GET    /              → lister tous les comptes
 *   GET    /{num}         → consulter un compte
 *   GET    /{num}/balance → consulter le solde
 *   POST   /deposit       → effectuer un dépôt
 *   POST   /withdraw      → effectuer un retrait
 *   POST   /transfer      → effectuer un transfert
 *   GET    /{num}/history → historique des transactions
 *
 * Interface Swagger disponible sur : http://localhost:8080/swagger-ui.html
 * ─────────────────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Comptes", description = "Gestion des comptes bancaires et des opérations de paiement")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── Gestion des comptes ────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un nouveau compte bancaire")
    public Account createAccount(@Valid @RequestBody AccountCreateRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping
    @Operation(summary = "Lister tous les comptes")
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Consulter un compte par son numéro")
    public Account getAccount(@PathVariable String accountNumber) {
        return accountService.getAccount(accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Consulter le solde d'un compte")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String accountNumber) {
        BigDecimal balance = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(Map.of(
                "accountNumber", accountNumber,
                "balance", balance
        ));
    }

    // ── Opérations financières ─────────────────────────────────────────────────

    @PostMapping("/deposit")
    @Operation(summary = "Effectuer un dépôt sur un compte")
    public ResponseEntity<Transaction> deposit(@Valid @RequestBody DepositRequest request) {
        Transaction tx = accountService.deposit(
                request.getAccountNumber(),
                request.getAmount(),
                request.getDescription()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Effectuer un retrait depuis un compte")
    public ResponseEntity<Transaction> withdraw(@Valid @RequestBody WithdrawRequest request) {
        Transaction tx = accountService.withdraw(
                request.getAccountNumber(),
                request.getAmount(),
                request.getDescription()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Effectuer un transfert entre deux comptes")
    public ResponseEntity<Transaction> transfer(@Valid @RequestBody TransferRequest request) {
        Transaction tx = accountService.transfer(
                request.getFromAccountNumber(),
                request.getToAccountNumber(),
                request.getAmount(),
                request.getDescription()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @GetMapping("/{accountNumber}/history")
    @Operation(summary = "Consulter l'historique des transactions d'un compte")
    public List<Transaction> getHistory(@PathVariable String accountNumber) {
        return accountService.getTransactionHistory(accountNumber);
    }
}
