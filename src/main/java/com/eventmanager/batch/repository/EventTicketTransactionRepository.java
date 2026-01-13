package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventTicketTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface EventTicketTransactionRepository extends JpaRepository<EventTicketTransaction, Long> {

    /**
     * Find distinct tenant IDs from event_ticket_transaction table.
     * Used for multi-tenant processing.
     */
    @Query("SELECT DISTINCT t.tenantId FROM EventTicketTransaction t WHERE t.tenantId IS NOT NULL ORDER BY t.tenantId")
    List<String> findDistinctTenantIds();

    /**
     * Find transactions that need fee/tax updates for a specific tenant.
     * Used when processing a single tenant.
     * Note: Uses purchaseDate instead of createdAt for date filtering to ensure proper 14-day delay logic.
     * Note: startDate and endDate should never be null - use default values in service layer.
     */
    @Query(value = "SELECT t.* FROM event_ticket_transaction t " +
           "WHERE t.tenant_id = :tenantId " +
           "AND (:eventId IS NULL OR t.event_id = :eventId) " +
           "AND (t.stripe_fee_amount IS NULL OR t.stripe_fee_amount = 0 OR :forceUpdate = true) " +
           "AND t.stripe_payment_intent_id IS NOT NULL " +
           "AND t.status = 'COMPLETED' " +
           "AND t.purchase_date >= :startDate " +
           "AND t.purchase_date <= :endDate " +
           "ORDER BY t.purchase_date DESC",
           nativeQuery = true)
    Page<EventTicketTransaction> findTransactionsNeedingUpdate(
        @Param("tenantId") String tenantId,
        @Param("eventId") Long eventId,
        @Param("forceUpdate") boolean forceUpdate,
        @Param("startDate") java.sql.Timestamp startDate,
        @Param("endDate") java.sql.Timestamp endDate,
        Pageable pageable
    );

    /**
     * Count transactions that need fee/tax updates for a specific tenant.
     * Note: Uses purchaseDate instead of createdAt for date filtering.
     * Note: startDate and endDate should never be null - use default values in service layer.
     */
    @Query(value = "SELECT COUNT(t.id) FROM event_ticket_transaction t " +
           "WHERE t.tenant_id = :tenantId " +
           "AND (:eventId IS NULL OR t.event_id = :eventId) " +
           "AND (t.stripe_fee_amount IS NULL OR t.stripe_fee_amount = 0 OR :forceUpdate = true) " +
           "AND t.stripe_payment_intent_id IS NOT NULL " +
           "AND t.status = 'COMPLETED' " +
           "AND t.purchase_date >= :startDate " +
           "AND t.purchase_date <= :endDate",
           nativeQuery = true)
    long countTransactionsNeedingUpdate(
        @Param("tenantId") String tenantId,
        @Param("eventId") Long eventId,
        @Param("forceUpdate") boolean forceUpdate,
        @Param("startDate") java.sql.Timestamp startDate,
        @Param("endDate") java.sql.Timestamp endDate
    );
}
