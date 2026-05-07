package cm.ict304.payment.service;

import cm.ict304.payment.dto.AccountCreateRequest;
import cm.ict304.payment.exception.AccountNotFoundException;
import cm.ict304.payment.exception.AccountSuspendedException;
import cm.ict304.payment.exception.InsufficientFundsException;
import cm.ict304.payment.model.*;
import cm.ict304.payment.repository.AccountRepository;
import cm.ict304.payment.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES — AccountService (ICT304)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Techniques ICT304 appliquées :
 *
 *  ┌─────────────────────────────────────────────────────────────────────────┐
 *  │ BOÎTE BLANCHE (White-Box)                                               │
 *  │  • CFG analysé pour chaque méthode du service                           │
 *  │  • Couverture C1 (instructions) : tous les chemins linéaires testés     │
 *  │  • Couverture C2 (branches)     : les deux côtés de chaque if testés    │
 *  ├─────────────────────────────────────────────────────────────────────────┤
 *  │ BOÎTE NOIRE (Black-Box)                                                 │
 *  │  • Partitionnement par équivalence sur les montants                     │
 *  │  • Analyse aux valeurs limites (0, 0.01, montant exact du solde)        │
 *  │  • Tests avec @ParameterizedTest sur plusieurs valeurs                  │
 *  ├─────────────────────────────────────────────────────────────────────────┤
 *  │ FLUX DE DONNÉES (DFT)                                                   │
 *  │  • Suivi des variables : account (def → use), amount (def → use)        │
 *  │  • balance : DEF dans credit()/debit(), USE dans hasSufficientFunds()   │
 *  ├─────────────────────────────────────────────────────────────────────────┤
 *  │ TESTS DE MUTATION (préparation pour PIT)                                │
 *  │  • Tests vérifiant les conditions limites exactes                       │
 *  │  • Ces tests tueront les mutants AOR, ROR, NEGATE_CONDITIONALS         │
 *  └─────────────────────────────────────────────────────────────────────────┘
 *
 * Architecture : Mockito est utilisé pour isoler le service de la BD.
 * On teste UNIQUEMENT la logique métier du service, pas JPA.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitaires — AccountService")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Crée un compte ACTIVE avec le solde donné pour les tests.
     * DFT : DEF(account) dans cette méthode utilitaire.
     */
    private Account activeAccount(String num, BigDecimal balance) {
        Account a = new Account();
        a.setAccountNumber(num);
        a.setOwnerName("Test User");
        a.setBalance(balance);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private Account suspendedAccount(String num) {
        Account a = activeAccount(num, BigDecimal.valueOf(500));
        a.setStatus(AccountStatus.SUSPENDED);
        return a;
    }

    private Transaction savedTx() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        return tx;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1 : CRÉATION DE COMPTE
    // Technique : CFG à chemin unique → 1 cas de test suffit pour C1
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Création de compte")
    class CreateAccountTests {

        @Test
        @DisplayName("CT-1.1 : Créer un compte avec solde initial → succès")
        void createAccount_withInitialBalance_shouldReturnSavedAccount() {
            // ARRANGE (Boîte noire : données d'entrée valides)
            AccountCreateRequest req = new AccountCreateRequest("Alice Dupont", BigDecimal.valueOf(1000));
            Account saved = activeAccount("PAY-ABCD1234", BigDecimal.valueOf(1000));
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            // ACT
            Account result = accountService.createAccount(req);

            // ASSERT
            // DFT : USE(result) après DEF via accountRepository.save()
            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
            assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(accountRepository, times(1)).save(any(Account.class));
        }

        @Test
        @DisplayName("CT-1.2 : Créer un compte sans solde initial → solde = 0")
        void createAccount_withoutBalance_shouldDefaultToZero() {
            // Partitionnement par équivalence : classe "solde null"
            AccountCreateRequest req = new AccountCreateRequest("Bob Martin", null);
            Account saved = activeAccount("PAY-XXXX0000", BigDecimal.ZERO);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            Account result = accountService.createAccount(req);

            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2 : DÉPÔT
    // CFG : 3 chemins → couverture C2 = 3 cas de test
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Dépôt (deposit)")
    class DepositTests {

        /**
         * CT-2.1 : Chemin nominal (happy path)
         * CFG : [Trouver compte ✓] → [Statut ACTIVE ✓] → [Créditer] → [Enregistrer] → OK
         * DFT : DEF(account) → USE(account.credit) → USE(account.balance)
         */
        @Test
        @DisplayName("CT-2.1 : Compte ACTIVE → dépôt réussi, solde augmenté")
        void deposit_onActiveAccount_shouldIncreaseBalance() {
            Account account = activeAccount("PAY-AAA111", BigDecimal.valueOf(500));
            when(accountRepository.findByAccountNumber("PAY-AAA111")).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(savedTx());

            accountService.deposit("PAY-AAA111", BigDecimal.valueOf(200), "Virement");

            // USE(account.balance) : vérification de la valeur après crédit
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(700));
            verify(transactionRepository).save(argThat(tx ->
                    tx.getType() == TransactionType.DEPOSIT &&
                    tx.getAmount().compareTo(BigDecimal.valueOf(200)) == 0
            ));
        }

        /**
         * CT-2.2 : Branche "compte non trouvé"
         * CFG : [Trouver compte ✗] → AccountNotFoundException
         * Mutation target : si on supprime le orElseThrow → le mutant survit → ce test le tue
         */
        @Test
        @DisplayName("CT-2.2 : Compte inexistant → AccountNotFoundException")
        void deposit_onNonExistentAccount_shouldThrowAccountNotFoundException() {
            when(accountRepository.findByAccountNumber("PAY-UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deposit("PAY-UNKNOWN", BigDecimal.valueOf(100), "Test"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("PAY-UNKNOWN");

            // Vérification que la transaction n'a jamais été enregistrée
            verifyNoInteractions(transactionRepository);
        }

        /**
         * CT-2.3 : Branche "compte suspendu"
         * CFG : [Trouver compte ✓] → [Statut ≠ ACTIVE] → AccountSuspendedException
         * Mutation target : si on change != en == → le mutant survit → ce test le tue
         */
        @Test
        @DisplayName("CT-2.3 : Compte SUSPENDED → AccountSuspendedException")
        void deposit_onSuspendedAccount_shouldThrowAccountSuspendedException() {
            when(accountRepository.findByAccountNumber("PAY-SUSP")).thenReturn(
                    Optional.of(suspendedAccount("PAY-SUSP")));

            assertThatThrownBy(() -> accountService.deposit("PAY-SUSP", BigDecimal.valueOf(100), "Test"))
                    .isInstanceOf(AccountSuspendedException.class);

            verifyNoInteractions(transactionRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3 : RETRAIT
    // CFG : 4 chemins → couverture C2 = 4 cas de test
    // Test de mutation : opérateurs >= dans hasSufficientFunds
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Retrait (withdraw)")
    class WithdrawTests {

        @Test
        @DisplayName("CT-3.1 : Solde suffisant → retrait réussi, balance diminuée")
        void withdraw_withSufficientFunds_shouldDecreaseBalance() {
            Account account = activeAccount("PAY-BBB222", BigDecimal.valueOf(1000));
            when(accountRepository.findByAccountNumber("PAY-BBB222")).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(savedTx());

            accountService.withdraw("PAY-BBB222", BigDecimal.valueOf(300), "Retrait ATM");

            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(700));
        }

        @Test
        @DisplayName("CT-3.2 : Compte inexistant → AccountNotFoundException")
        void withdraw_onNonExistentAccount_shouldThrowException() {
            when(accountRepository.findByAccountNumber("PAY-GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.withdraw("PAY-GHOST", BigDecimal.valueOf(100), "Test"))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("CT-3.3 : Compte suspendu → AccountSuspendedException")
        void withdraw_onSuspendedAccount_shouldThrowException() {
            when(accountRepository.findByAccountNumber("PAY-SUSP2")).thenReturn(
                    Optional.of(suspendedAccount("PAY-SUSP2")));

            assertThatThrownBy(() -> accountService.withdraw("PAY-SUSP2", BigDecimal.valueOf(50), "Test"))
                    .isInstanceOf(AccountSuspendedException.class);
        }

        /**
         * CT-3.4 : Solde insuffisant → InsufficientFundsException
         *
         * TEST DE MUTATION CRITIQUE :
         * Mutant généré par PIT → change >= en > dans hasSufficientFunds()
         * Ce test DOIT tuer ce mutant en testant le cas limite exact (montant == solde)
         *
         * DFT All-P-Use : USE(balance) dans la condition hasSufficientFunds
         */
        @Test
        @DisplayName("CT-3.4 : Solde insuffisant → InsufficientFundsException")
        void withdraw_withInsufficientFunds_shouldThrowException() {
            Account account = activeAccount("PAY-CCC333", BigDecimal.valueOf(100));
            when(accountRepository.findByAccountNumber("PAY-CCC333")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.withdraw("PAY-CCC333", BigDecimal.valueOf(150), "Surcharge"))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("PAY-CCC333");

            // Le solde NE DOIT PAS avoir changé
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
            verifyNoInteractions(transactionRepository);
        }

        /**
         * CT-3.5 : Valeur limite — retrait exactement égal au solde
         *
         * ANALYSE AUX VALEURS LIMITES (boîte noire) :
         * Partition valide : [0.01, solde]. La frontière est amount == balance.
         *
         * TEST DE MUTATION : tue le mutant qui change >= en > dans hasSufficientFunds()
         * Si PIT génère : if (balance.compareTo(amount) > 0) au lieu de >= 0
         * → ce cas (amount == balance) retournerait false → erreur → test KILL le mutant
         */
        @Test
        @DisplayName("CT-3.5 [Valeur limite] : Retrait = solde exact → AUTORISÉ")
        void withdraw_exactBalance_shouldSucceed() {
            BigDecimal exactBalance = BigDecimal.valueOf(500);
            Account account = activeAccount("PAY-EXACT", exactBalance);
            when(accountRepository.findByAccountNumber("PAY-EXACT")).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(savedTx());

            // Doit réussir (solde suffisant)
            assertThatCode(() ->
                    accountService.withdraw("PAY-EXACT", exactBalance, "Retrait exact")
            ).doesNotThrowAnyException();

            // USE(balance) : doit être 0 après débit total
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 4 : TRANSFERT
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Transfert (transfer)")
    class TransferTests {

        @Test
        @DisplayName("CT-4.1 : Transfert valide entre deux comptes actifs → succès")
        void transfer_valid_shouldDebitSourceAndCreditDestination() {
            Account source = activeAccount("PAY-SRC", BigDecimal.valueOf(1000));
            Account dest   = activeAccount("PAY-DST", BigDecimal.valueOf(200));
            when(accountRepository.findByAccountNumber("PAY-SRC")).thenReturn(Optional.of(source));
            when(accountRepository.findByAccountNumber("PAY-DST")).thenReturn(Optional.of(dest));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenReturn(savedTx());

            accountService.transfer("PAY-SRC", "PAY-DST", BigDecimal.valueOf(400), "Loyer");

            // DFT All-C-Use : USE(source.balance) et USE(dest.balance)
            assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
            assertThat(dest.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
        }

        @Test
        @DisplayName("CT-4.2 : Transfert vers soi-même → IllegalArgumentException")
        void transfer_toSameAccount_shouldThrowIllegalArgument() {
            assertThatThrownBy(() ->
                    accountService.transfer("PAY-SAME", "PAY-SAME", BigDecimal.valueOf(100), "Auto")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("identiques");

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("CT-4.3 : Source avec solde insuffisant → InsufficientFundsException")
        void transfer_insufficientSource_shouldThrowException() {
            Account source = activeAccount("PAY-POOR", BigDecimal.valueOf(50));
            Account dest   = activeAccount("PAY-RICH", BigDecimal.valueOf(1000));
            when(accountRepository.findByAccountNumber("PAY-POOR")).thenReturn(Optional.of(source));
            when(accountRepository.findByAccountNumber("PAY-RICH")).thenReturn(Optional.of(dest));

            assertThatThrownBy(() ->
                    accountService.transfer("PAY-POOR", "PAY-RICH", BigDecimal.valueOf(200), "Impossible")
            ).isInstanceOf(InsufficientFundsException.class);

            // Les deux comptes doivent rester inchangés (atomicité @Transactional)
            assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50));
            assertThat(dest.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 5 : TESTS BOÎTE NOIRE — Partitionnement par équivalence
    // Technique : @ParameterizedTest pour couvrir plusieurs classes en un test
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Boîte noire — Partitionnement par équivalence (montants)")
    class BlackBoxTests {

        /**
         * Partition INVALIDE : montants négatifs ou nuls
         * Une seule valeur représente toute la classe invalide.
         * PIT MUTATION : tue les mutants qui changent <= 0 en < 0
         */
        @ParameterizedTest(name = "Montant invalide {0} → rejeté")
        @ValueSource(doubles = {-100.0, -0.01, 0.0})
        @DisplayName("CT-5.1 : Montants invalides (≤ 0) → exception Account.debit/credit")
        void deposit_invalidAmounts_shouldBeRejected(double invalidAmount) {
            Account account = activeAccount("PAY-TEST", BigDecimal.valueOf(1000));
            when(accountRepository.findByAccountNumber("PAY-TEST")).thenReturn(Optional.of(account));

            BigDecimal amount = BigDecimal.valueOf(invalidAmount);

            assertThatThrownBy(() -> accountService.deposit("PAY-TEST", amount, "test"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * Partition VALIDE : montants positifs
         * Plusieurs représentants : petit, moyen, grand
         */
        @ParameterizedTest(name = "Montant valide {0} → accepté")
        @ValueSource(doubles = {0.01, 1.0, 100.0, 9999.99})
        @DisplayName("CT-5.2 : Montants valides (> 0) → dépôt accepté")
        void deposit_validAmounts_shouldBeAccepted(double validAmount) {
            Account account = activeAccount("PAY-VALID", BigDecimal.valueOf(0));
            when(accountRepository.findByAccountNumber("PAY-VALID")).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(savedTx());

            assertThatCode(() ->
                    accountService.deposit("PAY-VALID", BigDecimal.valueOf(validAmount), "test")
            ).doesNotThrowAnyException();
        }
    }
}
