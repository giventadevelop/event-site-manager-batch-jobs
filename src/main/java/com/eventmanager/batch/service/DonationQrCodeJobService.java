package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.dto.DonationQrCodeJobRequest;
import com.eventmanager.batch.dto.DonationQrCodeJobResponse;
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
 * Service for triggering donation QR code batch jobs.
 * Processes donations that need QR codes generated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DonationQrCodeJobService {

    private final JobLauncher jobLauncher;
    private final BatchJobExecutionService batchJobExecutionService;
    private final DonationTransactionRepository donationRepository;

    @Qualifier("donationQrCodeJob")
    private final Job donationQrCodeJob;

    /**
     * Trigger donation QR code batch job.
     * Processes all donations that need QR codes generated.
     */
    public DonationQrCodeJobResponse triggerDonationQrCodeJob(DonationQrCodeJobRequest request) {
        log.info("Received request to trigger donation QR code job - donationId: {}, eventId: {}, tenantId: {}",
            request.getDonationId(),
            request.getEventId(),
            request.getTenantId()
        );

        // Create job execution record
        String parametersJson = String.format(
            "{\"donationId\":%d,\"eventId\":%d,\"tenantId\":\"%s\"}",
            request.getDonationId(),
            request.getEventId(),
            request.getTenantId()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "donationQrCodeJob",
            "DONATION_QR_CODE",
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
            jobLauncher.run(donationQrCodeJob, jobParameters);

            return DonationQrCodeJobResponse.builder()
                .success(true)
                .message("Donation QR code job started successfully")
                .jobExecutionId(execution.getId())
                .qrCodeUrl(null) // Will be populated after job completes
                .qrCodeImageUrl(null) // Will be populated after job completes
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();

        } catch (Exception e) {
            log.error("Failed to start donation QR code job: {}", e.getMessage(), e);
            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L, 0L, 0L,
                e.getMessage()
            );

            return DonationQrCodeJobResponse.builder()
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
