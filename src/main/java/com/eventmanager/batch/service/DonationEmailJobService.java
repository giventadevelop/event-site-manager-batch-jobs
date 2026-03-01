package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.DonationEmailJobRequest;
import com.eventmanager.batch.dto.DonationEmailJobResponse;
import com.eventmanager.batch.repository.DonationTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for triggering donation email batch jobs.
 * Processes donations that need confirmation emails sent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DonationEmailJobService {

    private final JobLauncher jobLauncher;
    private final BatchJobExecutionService batchJobExecutionService;
    private final DonationTransactionRepository donationRepository;

    @Qualifier("donationEmailJob")
    private final Job donationEmailJob;

    /**
     * Trigger donation email batch job.
     * Processes all donations that need confirmation emails.
     */
    public DonationEmailJobResponse triggerDonationEmailJob(DonationEmailJobRequest request) {
        log.info("Received request to trigger donation email job - donationId: {}, eventId: {}, tenantId: {}",
            request.getDonationId(),
            request.getEventId(),
            request.getTenantId()
        );

        // Create job execution record
        String parametersJson = String.format(
            "{\"donationId\":%d,\"eventId\":%d,\"tenantId\":\"%s\",\"recipientEmail\":\"%s\"}",
            request.getDonationId(),
            request.getEventId(),
            request.getTenantId(),
            request.getRecipientEmail()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "donationEmailJob",
            "DONATION_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        try {
            // Build job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("jobId", UUID.randomUUID().toString())
                .addString("tenantId", request.getTenantId())
                .addLong("eventId", request.getEventId())
                .addLong("donationId", request.getDonationId())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            // Launch job asynchronously
            jobLauncher.run(donationEmailJob, jobParameters);

            return DonationEmailJobResponse.builder()
                .success(true)
                .message("Donation email job started successfully")
                .jobExecutionId(execution.getId())
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();

        } catch (Exception e) {
            log.error("Failed to start donation email job: {}", e.getMessage(), e);
            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L, 0L, 0L,
                e.getMessage()
            );

            return DonationEmailJobResponse.builder()
                .success(false)
                .message("Failed to start job: " + e.getMessage())
                .jobExecutionId(execution.getId())
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }
    }
}
