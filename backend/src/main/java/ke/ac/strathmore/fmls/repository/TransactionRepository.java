package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t from Transaction t join fetch t.product join fetch t.farmer join fetch t.buyer "
            + "where t.buyer.buyerId = :buyerId order by t.transactionDate desc")
    List<Transaction> forBuyer(@Param("buyerId") Long buyerId);

    @Query("select t from Transaction t join fetch t.product join fetch t.farmer join fetch t.buyer "
            + "where t.farmer.farmerId = :farmerId and t.transactionDate >= :from order by t.transactionDate desc")
    List<Transaction> forFarmerSince(@Param("farmerId") Long farmerId, @Param("from") LocalDateTime from);

    /** Demand history for one crop (quantity per transaction date). */
    @Query("select t.transactionDate, t.quantity from Transaction t "
            + "where lower(t.product.cropType) = lower(:crop) and t.status <> :excluded")
    List<Object[]> demandHistory(@Param("crop") String crop, @Param("excluded") TransactionStatus excluded);

    /** Compact projection for model training: crop, date, quantity. */
    @Query("select t.product.cropType, t.transactionDate, t.quantity from Transaction t where t.status <> :excluded")
    List<Object[]> trainingRows(@Param("excluded") TransactionStatus excluded);

    /** How many times each buyer bought each crop (purchase-history component of matching). */
    @Query("select t.buyer.buyerId, t.product.cropType, count(t) from Transaction t "
            + "where t.status <> :excluded group by t.buyer.buyerId, t.product.cropType")
    List<Object[]> purchaseCounts(@Param("excluded") TransactionStatus excluded);

    @Query("select t.product.cropType, count(t), sum(t.quantity), sum(t.quantity * t.agreedPrice) from Transaction t "
            + "where t.status <> :excluded and t.transactionDate >= :from group by t.product.cropType")
    List<Object[]> summaryByCrop(@Param("excluded") TransactionStatus excluded, @Param("from") LocalDateTime from);

    @Query("select year(t.transactionDate), month(t.transactionDate), count(t), sum(t.quantity * t.agreedPrice) "
            + "from Transaction t where t.status <> :excluded and t.transactionDate >= :from "
            + "group by year(t.transactionDate), month(t.transactionDate) "
            + "order by year(t.transactionDate), month(t.transactionDate)")
    List<Object[]> monthlyVolume(@Param("excluded") TransactionStatus excluded, @Param("from") LocalDateTime from);

    long countByStatus(TransactionStatus status);

    long countByTransactionDateAfter(LocalDateTime from);

    @Query("select t from Transaction t join fetch t.product join fetch t.farmer join fetch t.buyer "
            + "where t.transactionDate >= :from order by t.transactionDate desc")
    List<Transaction> since(@Param("from") LocalDateTime from);
}
