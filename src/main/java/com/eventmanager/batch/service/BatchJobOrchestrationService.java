package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.BatchJobResponse;
import com.eventmanager.batch.job.email.processor.EmailBatchProcessor;
import com.eventmanager.batch.job.email.reader.EmailBatchReader;
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

import java.util.List;
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

    @Qualifier("emailBatchJob")
    private final Job emailBatchJob;

    private final SubscriptionRenewalReader subscriptionRenewalReader;
    private final SubscriptionRenewalProcessor subscriptionRenewalProcessor;
    private final EmailBatchReader emailBatchReader;
    private final EmailBatchProcessor emailBatchProcessor;
    private final BatchJobExecutionService batchJobExecutionService;

    @Value("${batch.subscription-renewal.batch-size:100}")
    private int defaultBatchSize;

    @Value("${batch.subscription-renewal.max-subscriptions:10000}")
    private int defaultMaxSubscriptions;

    @Value("${batch.email.batch-size:50}")
    private int defaultEmailBatchSize;

    @Value("${batch.email.max-emails:10000}")
    private int defaultMaxEmails;

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
     */
    public BatchJobResponse runEmailBatchJob(
        String tenantId,
        Integer batchSize,
        Integer maxEmails,
        Long templateId,
        List<String> recipientEmails,
        Long userId,
        String recipientType
    ) {
        log.info("Starting email batch job for tenant: {}, templateId: {}", tenantId, templateId);

        if (templateId == null) {
            return BatchJobResponse.builder()
                .success(false)
                .message("Template ID is required for email batch job")
                .build();
        }

        if (tenantId == null || tenantId.isEmpty()) {
            return BatchJobResponse.builder()
                .success(false)
                .message("Tenant ID is required for email batch job")
                .build();
        }

        // Create job execution record
        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "emailBatchJob",
            "EMAIL_BATCH",
            tenantId,
            "API",
            String.format("{\"tenantId\":\"%s\",\"templateId\":%d,\"batchSize\":%d,\"maxEmails\":%d,\"recipientCount\":%d}",
                tenantId,
                templateId,
                batchSize != null ? batchSize : defaultEmailBatchSize,
                maxEmails != null ? maxEmails : defaultMaxEmails,
                recipientEmails != null ? recipientEmails.size() : 0)
        );

        try {
            // Initialize reader and processor
            int finalBatchSize = batchSize != null ? batchSize : defaultEmailBatchSize;
            int finalMaxEmails = maxEmails != null ? maxEmails : defaultMaxEmails;

            emailBatchReader.initialize(templateId, tenantId, recipientEmails, userId, finalMaxEmails, recipientType);
            emailBatchProcessor.setTemplate(emailBatchReader.getTemplate());

            // Build job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("jobId", UUID.randomUUID().toString())
                .addString("tenantId", tenantId)
                .addLong("templateId", templateId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            // Launch job asynchronously
            jobLauncher.run(emailBatchJob, jobParameters);

            return BatchJobResponse.builder()
                .success(true)
                .message("Email batch job started successfully")
                .jobExecutionId(execution.getId())
                .build();

        } catch (Exception e) {
            log.error("Failed to start email batch job: {}", e.getMessage(), e);
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
}

