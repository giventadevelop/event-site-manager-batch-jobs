package com.eventmanager.batch.scheduler;

import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import com.eventmanager.batch.repository.MembershipSubscriptionRepository;
import com.eventmanager.batch.service.BatchJobOrchestrationService;
import com.eventmanager.batch.service.StripeFeesTaxUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for running batch jobs on a cron schedule.
 * Jobs can be scheduled from the frontend or run automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobScheduler {

    private final BatchJobOrchestrationService batchJobOrchestrationService;
    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final StripeFeesTaxUpdateService stripeFeesTaxUpdateService;
    private final EventTicketTransactionRepository transactionRepository;

    @Value("${batch.subscription-renewal.enabled:true}")
    private boolean subscriptionRenewalEnabled;

    @Value("${batch.email.enabled:true}")
    private boolean emailBatchEnabled;

    @Value("${batch.stripe-fees-tax.enabled:true}")
    private boolean stripeFeesTaxEnabled;

    /**
     * Scheduled subscription renewal job.
     * Runs every 6 hours by default (configurable via cron expression).
     * Processes each tenant individually to ensure proper tenant isolation.
     */
    @Scheduled(cron = "${batch.subscription-renewal.schedule-cron:0 0 */6 * * *}")
    public void scheduledSubscriptionRenewal() {
        if (!subscriptionRenewalEnabled) {
            log.debug("Subscription renewal job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.info("Starting scheduled subscription renewal batch job for all tenants");

            // Get all distinct tenant IDs from the database
            List<String> tenantIds = membershipSubscriptionRepository.findAllDistinctTenantIds();

            if (tenantIds == null || tenantIds.isEmpty()) {
                log.warn("No tenants found in database. Skipping scheduled subscription renewal job.");
                return;
            }

            log.info("Found {} tenant(s) to process: {}", tenantIds.size(), tenantIds);

            int successCount = 0;
            int failureCount = 0;

            // Process each tenant individually
            for (String tenantId : tenantIds) {
                try {
                    log.info("Processing subscription renewal for tenant: {}", tenantId);
                    batchJobOrchestrationService.runSubscriptionRenewalJob(tenantId, null, null);
                    successCount++;

                    // Small delay between tenants to avoid overwhelming the system
                    Thread.sleep(1000); // 1 second delay
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process subscription renewal for tenant {}: {}",
                        tenantId, e.getMessage(), e);
                    // Continue with next tenant even if one fails
                }
            }

            log.info("Completed scheduled subscription renewal batch job. " +
                    "Successfully processed: {}, Failed: {}, Total tenants: {}",
                    successCount, failureCount, tenantIds.size());

        } catch (Exception e) {
            log.error("Failed to execute scheduled subscription renewal job: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled email batch job.
     * Runs daily at 2 AM by default (configurable via cron expression).
     *
     * Note: Email batch jobs require a templateId and are typically triggered via API.
     * This scheduled job is disabled by default as it requires specific template configuration.
     * To enable scheduled email jobs, implement a mechanism to select templates or process pending emails.
     */
    @Scheduled(cron = "${batch.email.schedule-cron:0 0 2 * * *}")
    public void scheduledEmailBatch() {
        if (!emailBatchEnabled) {
            log.debug("Email batch job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.warn("Scheduled email batch job requires templateId. " +
                    "Email batch jobs should be triggered via API with specific template configuration. " +
                    "Skipping scheduled execution.");
            // TODO: Implement scheduled email batch job logic if needed
            // This could process pending email jobs or select templates based on configuration
        } catch (Exception e) {
            log.error("Failed to execute scheduled email batch job: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled Stripe fees and tax update job.
     * Runs daily at 2 AM by default (configurable via cron expression).
     * Processes all tenants with 14-day delay logic (only processes transactions from current month
     * that are at least 14 days old to ensure Stripe payout is complete).
     */
    @Scheduled(cron = "${batch.stripe-fees-tax.schedule-cron:0 0 2 * * *}")
    public void scheduledStripeFeesTaxUpdate() {
        if (!stripeFeesTaxEnabled) {
            log.debug("Stripe fees and tax update job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.info("Starting scheduled Stripe fees and tax update batch job for all tenants (14-day delay)");

            // Process all tenants (tenantId = null) and all events (eventId = null)
            // useDefaultDateRange = true will automatically calculate:
            // - Start Date: First day of current month
            // - End Date: Today minus 14 days
            stripeFeesTaxUpdateService.processStripeFeesAndTax(null, null, null, null, false, true)
                .thenAccept(stats -> {
                    log.info("Completed scheduled Stripe fees and tax update job. " +
                            "Tenants processed: {}, Transactions processed: {}, " +
                            "Successfully updated: {}, Failed: {}, Skipped: {}",
                        stats.totalTenantsProcessed, stats.totalProcessed,
                        stats.successfullyUpdated, stats.failed, stats.skipped);
                })
                .exceptionally(ex -> {
                    log.error("Failed to execute scheduled Stripe fees and tax update job: {}",
                        ex.getMessage(), ex);
                    return null;
                });

        } catch (Exception e) {
            log.error("Failed to start scheduled Stripe fees and tax update job: {}", e.getMessage(), e);
        }
    }
}




