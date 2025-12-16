package com.eventmanager.batch.scheduler;

import com.eventmanager.batch.repository.TenantSettingsRepository;
import com.eventmanager.batch.service.BatchJobOrchestrationService;
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
    private final TenantSettingsRepository tenantSettingsRepository;

    @Value("${batch.subscription-renewal.enabled:true}")
    private boolean subscriptionRenewalEnabled;

    @Value("${batch.email.enabled:true}")
    private boolean emailBatchEnabled;

    @Value("${batch.subscription-renewal.batch-size:100}")
    private int defaultBatchSize;

    @Value("${batch.subscription-renewal.max-subscriptions:10000}")
    private int defaultMaxSubscriptions;

    /**
     * Scheduled subscription renewal job.
     * Runs every 6 hours by default (configurable via cron expression).
     * Processes all tenants by querying tenant_settings table and triggering batch job for each tenant separately.
     */
    @Scheduled(cron = "${batch.subscription-renewal.schedule-cron:0 0 */6 * * *}")
    public void scheduledSubscriptionRenewal() {
        if (!subscriptionRenewalEnabled) {
            log.debug("Subscription renewal job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.info("Starting scheduled subscription renewal batch job for all tenants");

            // Query all unique tenant IDs from tenant_settings table
            List<String> tenantIds = tenantSettingsRepository.findAllDistinctTenantIds();
            log.info("Found {} tenants to process", tenantIds.size());

            if (tenantIds.isEmpty()) {
                log.warn("No tenants found in tenant_settings table, skipping batch job");
                return;
            }

            // Process each tenant separately
            int successCount = 0;
            int failureCount = 0;

            for (String tenantId : tenantIds) {
                try {
                    log.info("Processing tenant: {}", tenantId);

                    // Trigger batch job for this specific tenant
                    batchJobOrchestrationService.runSubscriptionRenewalJob(
                        tenantId,
                        defaultBatchSize,
                        defaultMaxSubscriptions,
                        null // No stripeSubscriptionId for scheduled jobs
                    );

                    successCount++;
                    log.info("Batch job submitted successfully for tenant: {}", tenantId);

                    // Rate limiting between tenants (optional - 1 second delay)
                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while processing tenants", e);
                    break;
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process tenant {}: {}", tenantId, e.getMessage(), e);
                    // Continue with next tenant even if one fails
                }
            }

            log.info("Completed scheduled subscription renewal batch job. " +
                    "Success: {}, Failures: {}, Total: {}", successCount, failureCount, tenantIds.size());

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
}




