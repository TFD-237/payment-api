package cm.ict304.payment.controller;

import cm.ict304.payment.model.Account;
import cm.ict304.payment.model.AccountStatus;
import cm.ict304.payment.repository.AccountRepository;
import cm.ict304.payment.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TESTS D'INTÉGRATION — AccountController + AccountService + H2
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Différence avec les tests unitaires :
 *   • On charge le contexte Spring complet (@SpringBootTest)
 *   • La vraie base de données H2 est utilisée (pas de mocks)
 *   • On teste le flux HTTP complet : requête → contrôleur → service → BDD → réponse
 *   • @Transactional : chaque test repart d'un état propre
 *
 * Technique ICT304 : TESTS SYSTÈME (niveau 3 de la pyramide des tests)
 *   On vérifie les exigences fonctionnelles de bout en bout.
 *
 * Chaque test vérifie :
 *   1. Le statut HTTP (200, 201, 404, 422...)
 *   2. La structure JSON de la réponse
 *   3. L'impact sur la base de données (si pertinent)
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Tests d'Intégration — API REST")
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Crée et sauvegarde un compte de test directement en BDD.
     * DFT : DEF(savedAccount) → USE dans les tests suivants
     */
    private Account createTestAccount(String owner, double balance) {
        Account account = new Account(owner, BigDecimal.valueOf(balance));
        return accountRepository.save(account);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDPOINT : POST /api/v1/accounts — Créer un compte
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class CreateAccountIT {

        @Test
        @DisplayName("IT-1.1 : Payload valide → 201 Created + numéro de compte généré")
        void createAccount_validPayload_returns201() throws Exception {
            Map<String, Object> body = Map.of(
                    "ownerName", "Jean Dupont",
                    "initialBalance", 500.00
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accountNumber", startsWith("PAY-")))
                    .andExpect(jsonPath("$.ownerName", is("Jean Dupont")))
                    .andExpect(jsonPath("$.balance", is(500.0)))
                    .andExpect(jsonPath("$.status", is("ACTIVE")));
        }

        @Test
        @DisplayName("IT-1.2 : Nom vide → 400 Bad Request avec erreur de validation")
        void createAccount_blankName_returns400() throws Exception {
            Map<String, Object> body = Map.of(
                    "ownerName", "",
                    "initialBalance", 100.00
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.ownerName").exists());
        }

        @Test
        @DisplayName("IT-1.3 : Solde initial négatif → 400 Bad Request")
        void createAccount_negativeBalance_returns400() throws Exception {
            Map<String, Object> body = Map.of(
                    "ownerName", "Alice",
                    "initialBalance", -50.00
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDPOINT : POST /api/v1/accounts/deposit — Dépôt
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/accounts/deposit")
    class DepositIT {

        @Test
        @DisplayName("IT-2.1 : Dépôt valide → 201 + transaction enregistrée")
        void deposit_valid_returns201() throws Exception {
            Account account = createTestAccount("Marie Curie", 0.0);

            Map<String, Object> body = Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "amount", 750.50,
                    "description", "Salaire Novembre"
            );

            mockMvc.perform(post("/api/v1/accounts/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is("DEPOSIT")))
                    .andExpect(jsonPath("$.amount", is(750.5)))
                    .andExpect(jsonPath("$.reference", startsWith("TXN-")));
        }

        @Test
        @DisplayName("IT-2.2 : Compte inconnu → 404 Not Found")
        void deposit_unknownAccount_returns404() throws Exception {
            Map<String, Object> body = Map.of(
                    "accountNumber", "PAY-INCONNU",
                    "amount", 100.00
            );

            mockMvc.perform(post("/api/v1/accounts/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("PAY-INCONNU")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDPOINT : POST /api/v1/accounts/withdraw — Retrait
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/accounts/withdraw")
    class WithdrawIT {

        @Test
        @DisplayName("IT-3.1 : Retrait avec solde suffisant → 201 + balance mise à jour")
        void withdraw_sufficientFunds_returns201() throws Exception {
            Account account = createTestAccount("Paul Valery", 1000.0);

            Map<String, Object> body = Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "amount", 300.00,
                    "description", "Loyer"
            );

            mockMvc.perform(post("/api/v1/accounts/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is("WITHDRAWAL")));

            // Vérification en base : solde mis à jour
            Account updated = accountRepository.findByAccountNumber(account.getAccountNumber()).orElseThrow();
            Assertions.assertEquals(0, updated.getBalance().compareTo(BigDecimal.valueOf(700.0)));
        }

        @Test
        @DisplayName("IT-3.2 : Solde insuffisant → 422 Unprocessable Entity")
        void withdraw_insufficientFunds_returns422() throws Exception {
            Account account = createTestAccount("Jean Valjean", 10.0);

            Map<String, Object> body = Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "amount", 500.00
            );

            mockMvc.perform(post("/api/v1/accounts/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message", containsString("insuffisants")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDPOINT : POST /api/v1/accounts/transfer — Transfert
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/accounts/transfer")
    class TransferIT {

        @Test
        @DisplayName("IT-4.1 : Transfert valide → les deux soldes mis à jour atomiquement")
        void transfer_valid_updatesBothAccounts() throws Exception {
            Account source = createTestAccount("Emetteur", 1000.0);
            Account dest   = createTestAccount("Receveur", 200.0);

            Map<String, Object> body = Map.of(
                    "fromAccountNumber", source.getAccountNumber(),
                    "toAccountNumber", dest.getAccountNumber(),
                    "amount", 500.00,
                    "description", "Remboursement"
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is("TRANSFER")));

            Account updatedSource = accountRepository.findByAccountNumber(source.getAccountNumber()).orElseThrow();
            Account updatedDest   = accountRepository.findByAccountNumber(dest.getAccountNumber()).orElseThrow();

            Assertions.assertEquals(0, updatedSource.getBalance().compareTo(BigDecimal.valueOf(500.0)));
            Assertions.assertEquals(0, updatedDest.getBalance().compareTo(BigDecimal.valueOf(700.0)));
        }

        @Test
        @DisplayName("IT-4.2 : Transfert vers soi-même → 400 Bad Request")
        void transfer_selfTransfer_returns400() throws Exception {
            Account account = createTestAccount("Narcisse", 500.0);

            Map<String, Object> body = Map.of(
                    "fromAccountNumber", account.getAccountNumber(),
                    "toAccountNumber", account.getAccountNumber(),
                    "amount", 100.00
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDPOINT : GET /{num}/history — Historique
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /{accountNumber}/history")
    class HistoryIT {

        @Test
        @DisplayName("IT-5.1 : Historique d'un compte avec transactions → liste non vide")
        void getHistory_afterTransactions_returnsNonEmptyList() throws Exception {
            Account account = createTestAccount("Historique User", 500.0);
            String num = account.getAccountNumber();

            // Effectuer un dépôt via l'API pour créer une transaction réelle
            mockMvc.perform(post("/api/v1/accounts/deposit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("accountNumber", num, "amount", 100.0))));

            mockMvc.perform(get("/api/v1/accounts/{num}/history", num))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }
    }
}
