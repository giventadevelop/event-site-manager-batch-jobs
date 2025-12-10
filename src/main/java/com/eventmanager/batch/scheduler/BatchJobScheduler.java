package com.eventmanager.batch.scheduler;

import com.eventmanager.batch.service.BatchJobOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for running batch jobs on a cron schedule.
 * Jobs can be scheduled from the frontend or run automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobScheduler {

    private final BatchJobOrchestrationService batchJobOrchestrationService;

    @Value("${batch.subscription-renewal.enabled:true}")
    private boolean subscriptionRenewalEnabled;

    @Value("${batch.email.enabled:true}")
    private boolean emailBatchEnabled;

    /**
     * Scheduled subscription renewal job.
     * Runs every 6 hours by default (configurable via cron expression).
     */
    @Scheduled(cron = "${batch.subscription-renewal.schedule-cron:0 0 */6 * * *}")
    public void scheduledSubscriptionRenewal() {
        if (!subscriptionRenewalEnabled) {
            log.debug("Subscription renewal job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.info("Starting scheduled subscription renewal batch job");
            batchJobOrchestrationService.runSubscriptionRenewalJob(null, null, null);
        } catch (Exception e) {
            log.error("Failed to execute scheduled subscription renewal job: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled email batch job.
     * Runs daily at 2 AM by default (configurable via cron expression).
     */
    @Scheduled(cron = "${batch.email.schedule-cron:0 0 2 * * *}")
    public void scheduledEmailBatch() {
        if (!emailBatchEnabled) {
            log.debug("Email batch job is disabled, skipping scheduled execution");
            return;
        }

        try {
            log.info("Starting scheduled email batch job");
            batchJobOrchestrationService.runEmailBatchJob(null, null, null);
        } catch (Exception e) {
            log.error("Failed to execute scheduled email batch job: {}", e.getMessage(), e);
        }
    }
}


