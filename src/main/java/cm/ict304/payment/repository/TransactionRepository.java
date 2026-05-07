package cm.ict304.payment.repository;

import cm.ict304.payment.model.Transaction;
import cm.ict304.payment.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Toutes les transactions impliquant un compte (en entrée ou sortie) */
    @Query("SELECT t FROM Transaction t WHERE t.fromAccountNumber = :num OR t.toAccountNumber = :num ORDER BY t.executedAt DESC")
    List<Transaction> findAllByAccountNumber(@Param("num") String accountNumber);

    List<Transaction> findByTypeOrderByExecutedAtDesc(TransactionType type);

    @Query("SELECT t FROM Transaction t WHERE (t.fromAccountNumber = :num OR t.toAccountNumber = :num) AND t.executedAt BETWEEN :from AND :to ORDER BY t.executedAt DESC")
    List<Transaction> findByAccountAndPeriod(@Param("num") String accountNumber,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}
