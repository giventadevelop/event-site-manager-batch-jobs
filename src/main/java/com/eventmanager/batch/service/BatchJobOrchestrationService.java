package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.BatchJobResponse;
import com.eventmanager.batch.job.subscription.processor.SubscriptionRenewalProcessor;
import com.eventmanager.batch.job.subscription.reader.SubscriptionRenewalReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for orchestrating batch job execution.
 * Handles job triggering, parameter setup, and execution tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobOrchestrationService {

    private final JobLauncher jobLauncher;

    @Qualifier("subscriptionRenewalJob")
    private final Job subscriptionRenewalJob;

    private final SubscriptionRenewalReader subscriptionRenewalReader;
    private final SubscriptionRenewalProcessor subscriptionRenewalProcessor;
    private final BatchJobExecutionService batchJobExecutionService;

    @Value("${batch.subscription-renewal.batch-size:100}")
    private int defaultBatchSize;

    @Value("${batch.subscription-renewal.max-subscriptions:10000}")
    private int defaultMaxSubscriptions;

    /**
     * Run subscription renewal batch job.
     */
    public BatchJobResponse runSubscriptionRenewalJob(String tenantId, Integer batchSize, Integer maxSubscriptions) {
        log.info("Starting subscription renewal batch job for tenant: {}", tenantId);

        // Create job execution record
        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "subscriptionRenewalJob",
            "SUBSCRIPTION_RENEWAL",
            tenantId,
            "API",
            String.format("{\"tenantId\":\"%s\",\"batchSize\":%d,\"maxSubscriptions\":%d}",
                tenantId, batchSize != null ? batchSize : defaultBatchSize,
                maxSubscriptions != null ? maxSubscriptions : defaultMaxSubscriptions)
        );

        try {
            // Configure reader and processor for tenant
            if (tenantId != null && !tenantId.isEmpty()) {
                subscriptionRenewalReader.setTenantId(tenantId);
                subscriptionRenewalProcessor.setTenantId(tenantId);
            }

            // Build job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("jobId", UUID.randomUUID().toString())
                .addString("tenantId", tenantId != null ? tenantId : "ALL")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            // Launch job asynchronously
            jobLauncher.run(subscriptionRenewalJob, jobParameters);

            // Note: In a real implementation, you'd want to track job completion asynchronously
            // For now, we'll return immediately with the execution ID

            return BatchJobResponse.builder()
                .success(true)
                .message("Subscription renewal job started successfully")
                .jobExecutionId(execution.getId())
                .build();

        } catch (Exception e) {
            log.error("Failed to start subscription renewal job: {}", e.getMessage(), e);
            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L, 0L, 0L,
                e.getMessage()
            );

            return BatchJobResponse.builder()
                .success(false)
                .message("Failed to start job: " + e.getMessage())
                .jobExecutionId(execution.getId())
                .build();
        }
    }

    /**
     * Run email batch job.
     * TODO: Implement email batch job
     */
    public BatchJobResponse runEmailBatchJob(String tenantId, Integer batchSize, Integer maxEmails) {
        log.info("Email batch job not yet implemented");

        return BatchJobResponse.builder()
            .success(false)
            .message("Email batch job not yet implemented")
            .build();
    }
}

