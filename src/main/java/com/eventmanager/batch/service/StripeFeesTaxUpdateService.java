package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.EventTicketTransaction;
import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for batch updating Stripe fees and tax data for event ticket transactions.
 * Supports multi-tenant processing and both scheduled and on-demand execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeFeesTaxUpdateService {

    private final EventTicketTransactionRepository transactionRepository;
    private final StripeFeesTaxService stripeFeesTaxService;
    private final BatchJobExecutionService batchJobExecutionService;

    @Value("${batch.stripe-fees-tax.batch-size:100}")
    private int defaultBatchSize;

    @Value("${batch.stripe-fees-tax.rate-limit-delay-ms:100}")
    private long rateLimitDelayMs;

    /**
     * Process Stripe fees and tax updates for transactions.
     * Supports multi-tenant processing: if tenantId is null, processes all tenants.
     *
     * @param tenantId Optional tenant ID to filter by (null = all tenants)
     * @param eventId Optional event ID to filter by (null = all events)
     * @param startDate Optional start date filter (ignored if useDefaultDateRange is true)
     * @param endDate Optional end date filter (ignored if useDefaultDateRange is true)
     * @param forceUpdate If true, update even if stripe_fee_amount is already populated
     * @param useDefaultDateRange If true, automatically calculate date range (first of month to 14 days ago)
     * @return Statistics about the processing
     */
    @Async
    public CompletableFuture<ProcessingStats> processStripeFeesAndTax(
        String tenantId,
        Long eventId,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        boolean forceUpdate,
        boolean useDefaultDateRange
    ) {
        // Clear API key cache at the start of each batch job run to ensure fresh data
        stripeFeesTaxService.clearAllApiKeyCache();

        ProcessingStats globalStats = new ProcessingStats();
        globalStats.startTime = ZonedDateTime.now();

        // If useDefaultDateRange is true, calculate dates automatically (14-day delay logic)
        if (useDefaultDateRange) {
            ZonedDateTime now = ZonedDateTime.now();
            // Start: First day of current month
            startDate = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            // End: Today minus 14 days (to ensure Stripe payout is complete)
            endDate = now.minusDays(14).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            log.info("Using default date range for normal batch run (14-day delay): {} to {}", startDate, endDate);
        }

        // Provide default dates if null (to avoid PostgreSQL type inference issues)
        if (startDate == null) {
            startDate = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneId.systemDefault());
        }
        if (endDate == null) {
            endDate = ZonedDateTime.of(2099, 12, 31, 23, 59, 59, 999999999, java.time.ZoneId.systemDefault());
        }

        log.info("Starting Stripe fees and tax update job - tenantId: {}, eventId: {}, startDate: {}, endDate: {}, forceUpdate: {}, useDefaultDateRange: {}",
            tenantId, eventId, startDate, endDate, forceUpdate, useDefaultDateRange);

        // Determine which tenants to process
        List<String> tenantsToProcess = new ArrayList<>();
        if (tenantId != null && !tenantId.isEmpty()) {
            tenantsToProcess.add(tenantId);
        } else {
            // Process all tenants
            tenantsToProcess = transactionRepository.findDistinctTenantIds();
            log.info("Processing all tenants: {} tenants found", tenantsToProcess.size());
        }

        if (tenantsToProcess.isEmpty()) {
            log.warn("No tenants found to process");
            globalStats.endTime = ZonedDateTime.now();
            return CompletableFuture.completedFuture(globalStats);
        }

        // Process each tenant sequentially
        for (String currentTenantId : tenantsToProcess) {
            log.info("Processing tenant: {}, eventId: {}", currentTenantId, eventId);
            TenantStats tenantStats = processTenantTransactions(
                currentTenantId,
                eventId,
                startDate,
                endDate,
                forceUpdate
            );

            globalStats.totalTenantsProcessed++;
            globalStats.totalProcessed += tenantStats.processed;
            globalStats.successfullyUpdated += tenantStats.updated;
            globalStats.failed += tenantStats.failed;
            globalStats.skipped += tenantStats.skipped;
            globalStats.totalFeesRetrieved = globalStats.totalFeesRetrieved.add(tenantStats.totalFees);
            globalStats.totalTaxRetrieved = globalStats.totalTaxRetrieved.add(tenantStats.totalTax);
            globalStats.tenantStats.add(tenantStats);

            log.info("Completed tenant {}: processed={}, updated={}, failed={}, skipped={}",
                currentTenantId, tenantStats.processed, tenantStats.updated,
                tenantStats.failed, tenantStats.skipped);
        }

        globalStats.endTime = ZonedDateTime.now();
        long durationMs = java.time.Duration.between(globalStats.startTime, globalStats.endTime).toMillis();
        globalStats.durationMs = durationMs;

        // Generate summary report
        log.info("=== Stripe Fees and Tax Update Job Summary ===");
        log.info("Total Tenants Processed: {}", globalStats.totalTenantsProcessed);
        log.info("Total Transactions Processed: {}", globalStats.totalProcessed);
        log.info("Successfully Updated: {}", globalStats.successfullyUpdated);
        log.info("Failed: {}", globalStats.failed);
        log.info("Skipped: {}", globalStats.skipped);
        log.info("Total Fees Retrieved: ${}", globalStats.totalFeesRetrieved);
        log.info("Total Tax Retrieved: ${}", globalStats.totalTaxRetrieved);
        log.info("Duration: {} ms", durationMs);

        if (globalStats.failed > 0 && globalStats.totalProcessed > 0) {
            double failureRate = (double) globalStats.failed / globalStats.totalProcessed * 100;
            String formattedRate = String.format("%.2f", failureRate);
            log.warn("Failure rate: {}%", formattedRate);
            if (failureRate > 10.0) {
                log.error("High failure rate detected: {}% (threshold: 10%)", formattedRate);
            }
        }

        return CompletableFuture.completedFuture(globalStats);
    }

    /**
     * Process transactions for a specific tenant.
     */
    private TenantStats processTenantTransactions(
        String tenantId,
        Long eventId,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        boolean forceUpdate
    ) {
        TenantStats stats = new TenantStats();
        stats.tenantId = tenantId;

        int batchSize = defaultBatchSize;
        int offset = 0;
        boolean hasMore = true;

        // Convert ZonedDateTime to Timestamp for native query
        java.sql.Timestamp startTimestamp = java.sql.Timestamp.from(startDate.toInstant());
        java.sql.Timestamp endTimestamp = java.sql.Timestamp.from(endDate.toInstant());

        while (hasMore) {
            Pageable pageable = PageRequest.of(offset / batchSize, batchSize);
            Page<EventTicketTransaction> transactions = transactionRepository.findTransactionsNeedingUpdate(
                tenantId, eventId, forceUpdate, startTimestamp, endTimestamp, pageable
            );

            if (transactions.isEmpty()) {
                hasMore = false;
                break;
            }

            for (EventTicketTransaction txn : transactions) {
                stats.processed++;

                try {
                    // Check if already populated (idempotency check, unless forceUpdate)
                    if (!forceUpdate && txn.getStripeFeeAmount() != null &&
                        txn.getStripeFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                        stats.skipped++;
                        continue;
                    }

                    // Retrieve Stripe fee and net amount (preferred method - more accurate)
                    StripeFeesTaxService.StripeFeeNetResult feeNetResult =
                        stripeFeesTaxService.getStripeFeeAndNet(tenantId, txn.getStripePaymentIntentId());
                    delay(rateLimitDelayMs);

                    BigDecimal stripeFee = null;
                    BigDecimal netPayoutFromStripe = null;
                    if (feeNetResult != null) {
                        stripeFee = feeNetResult.getFee();
                        netPayoutFromStripe = feeNetResult.getNet();
                    } else {
                        // Fallback to old method if new method fails
                        stripeFee = stripeFeesTaxService.getStripeFee(tenantId, txn.getStripePaymentIntentId());
                        delay(rateLimitDelayMs);
                    }

                    // Retrieve Stripe tax
                    BigDecimal stripeTax = stripeFeesTaxService.getStripeTax(
                        tenantId,
                        txn.getStripePaymentIntentId(),
                        txn.getStripeCheckoutSessionId()
                    );
                    delay(rateLimitDelayMs);

                    // Calculate net payout amount
                    // Use Stripe's net amount if available, otherwise calculate: final_amount - fee - tax
                    BigDecimal netPayoutAmount = null;
                    if (netPayoutFromStripe != null) {
                        // Use Stripe's calculated net amount (most accurate)
                        netPayoutAmount = netPayoutFromStripe;
                        log.debug("Using Stripe net amount {} for transaction {}", netPayoutAmount, txn.getId());
                    } else if (txn.getFinalAmount() != null) {
                        // Calculate: final_amount - stripe_fee_amount - stripe_amount_tax
                        netPayoutAmount = txn.getFinalAmount()
                            .subtract(stripeFee != null ? stripeFee : BigDecimal.ZERO)
                            .subtract(stripeTax != null ? stripeTax : BigDecimal.ZERO);
                        log.debug("Calculated net payout {} for transaction {} (final: {}, fee: {}, tax: {})",
                            netPayoutAmount, txn.getId(), txn.getFinalAmount(), stripeFee, stripeTax);
                    }

                    // Update database directly (both services share the same database)
                    try {
                        // Reload the entity to ensure we have the latest version
                        EventTicketTransaction transactionToUpdate = transactionRepository.findById(txn.getId())
                            .orElse(null);

                        if (transactionToUpdate == null) {
                            stats.failed++;
                            stats.errors.add(new TransactionError(txn.getId(), tenantId, "Transaction not found"));
                            log.warn("Transaction {} not found in database for tenant {}", txn.getId(), tenantId);
                            continue;
                        }

                        // Update the fields
                        transactionToUpdate.setStripeFeeAmount(stripeFee);
                        transactionToUpdate.setStripeAmountTax(stripeTax);
                        transactionToUpdate.setNetPayoutAmount(netPayoutAmount);

                        // Save to database
                        transactionRepository.save(transactionToUpdate);

                        stats.updated++;
                        if (stripeFee != null) {
                            stats.totalFees = stats.totalFees.add(stripeFee);
                        }
                        if (stripeTax != null) {
                            stats.totalTax = stats.totalTax.add(stripeTax);
                        }
                        log.debug("Successfully updated transaction {} for tenant {} - fee: {}, tax: {}, netPayout: {}",
                            txn.getId(), tenantId, stripeFee, stripeTax, netPayoutAmount);
                    } catch (Exception e) {
                        stats.failed++;
                        stats.errors.add(new TransactionError(txn.getId(), tenantId, "Database update failed: " + e.getMessage()));
                        log.error("Failed to update transaction {} for tenant {}: {}", txn.getId(), tenantId, e.getMessage(), e);
                    }
                } catch (Exception e) {
                    stats.failed++;
                    stats.errors.add(new TransactionError(txn.getId(), tenantId, e.getMessage()));
                    log.error("Error processing transaction {} for tenant {}: {}",
                        txn.getId(), tenantId, e.getMessage(), e);
                }
            }

            offset += transactions.getNumberOfElements();

            // Check if we've processed all transactions for this tenant
            if (transactions.getNumberOfElements() < batchSize) {
                hasMore = false;
            }
        }

        return stats;
    }

    /**
     * Delay to respect rate limits.
     */
    private void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted");
        }
    }

    /**
     * Statistics for the entire processing run.
     */
    public static class ProcessingStats {
        public int totalTenantsProcessed = 0;
        public long totalProcessed = 0;
        public long successfullyUpdated = 0;
        public long failed = 0;
        public long skipped = 0;
        public BigDecimal totalFeesRetrieved = BigDecimal.ZERO;
        public BigDecimal totalTaxRetrieved = BigDecimal.ZERO;
        public ZonedDateTime startTime;
        public ZonedDateTime endTime;
        public Long durationMs;
        public List<TenantStats> tenantStats = new ArrayList<>();
    }

    /**
     * Statistics for a single tenant.
     */
    public static class TenantStats {
        public String tenantId;
        public long processed = 0;
        public long updated = 0;
        public long failed = 0;
        public long skipped = 0;
        public BigDecimal totalFees = BigDecimal.ZERO;
        public BigDecimal totalTax = BigDecimal.ZERO;
        public List<TransactionError> errors = new ArrayList<>();
    }

    /**
     * Error information for a failed transaction.
     */
    public static class TransactionError {
        public Long transactionId;
        public String tenantId;
        public String error;

        public TransactionError(Long transactionId, String tenantId, String error) {
            this.transactionId = transactionId;
            this.tenantId = tenantId;
            this.error = error;
        }
    }
}
