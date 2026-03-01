package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.DonationTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DonationTransaction entity.
 */
@Repository
public interface DonationTransactionRepository extends JpaRepository<DonationTransaction, Long> {

    /**
     * Find donations that need email sent:
     * - Status is COMPLETED
     * - Email is not null
     * - email_sent is false
     */
    @Query("SELECT d FROM DonationTransaction d " +
           "WHERE d.status = 'COMPLETED' " +
           "AND d.email IS NOT NULL " +
           "AND d.emailSent = false " +
           "ORDER BY d.createdAt ASC")
    List<DonationTransaction> findDonationsNeedingEmail();

    /**
     * Find donations that need QR code:
     * - Status is COMPLETED
     * - event_id is not null
     * - qr_code_url is null
     */
    @Query("SELECT d FROM DonationTransaction d " +
           "WHERE d.status = 'COMPLETED' " +
           "AND d.eventId IS NOT NULL " +
           "AND d.qrCodeUrl IS NULL " +
           "ORDER BY d.createdAt ASC")
    List<DonationTransaction> findDonationsNeedingQrCode();

    /**
     * Find donation by transaction reference.
     */
    Optional<DonationTransaction> findByTransactionReference(String transactionReference);

    /**
     * Find donation by GiveButter donation ID.
     */
    Optional<DonationTransaction> findByGivebutterDonationId(String givebutterDonationId);

    /**
     * Find donations by event ID and status.
     */
    List<DonationTransaction> findByEventIdAndStatus(Long eventId, String status);

    /**
     * Find donations by tenant ID and status.
     */
    List<DonationTransaction> findByTenantIdAndStatus(String tenantId, String status);
}
