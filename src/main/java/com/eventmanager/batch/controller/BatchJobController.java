package com.eventmanager.batch.controller;

import com.eventmanager.batch.dto.BatchJobRequest;
import com.eventmanager.batch.dto.BatchJobResponse;
import com.eventmanager.batch.dto.ContactFormEmailJobRequest;
import com.eventmanager.batch.dto.ContactFormEmailJobResponse;
import com.eventmanager.batch.dto.PromotionTestEmailJobRequest;
import com.eventmanager.batch.dto.PromotionTestEmailJobResponse;
import com.eventmanager.batch.dto.StripeFeesTaxUpdateRequest;
import com.eventmanager.batch.dto.StripeFeesTaxUpdateResponse;
import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import com.eventmanager.batch.service.BatchJobOrchestrationService;
import com.eventmanager.batch.service.ContactFormEmailJobService;
import com.eventmanager.batch.service.PromotionTestEmailJobService;
import com.eventmanager.batch.service.StripeFeesTaxUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * REST API controller for triggering batch jobs programmatically.
 * Allows the main backend application to trigger batch jobs.
 */
@RestController
@RequestMapping("/api/batch-jobs")
@RequiredArgsConstructor
@Slf4j
public class BatchJobController {

    private final BatchJobOrchestrationService batchJobOrchestrationService;
    private final StripeFeesTaxUpdateService stripeFeesTaxUpdateService;
    private final ContactFormEmailJobService contactFormEmailJobService;
    private final PromotionTestEmailJobService promotionTestEmailJobService;
    private final EventTicketTransactionRepository transactionRepository;

    /**
     * Trigger subscription renewal batch job.
     */
    @PostMapping("/subscription-renewal")
    public ResponseEntity<BatchJobResponse> triggerSubscriptionRenewal(@RequestBody BatchJobRequest request) {
        try {
            log.info("Received request to trigger subscription renewal job for tenant: {}", request.getTenantId());

            BatchJobResponse response = batchJobOrchestrationService.runSubscriptionRenewalJob(
                request.getTenantId(),
                request.getBatchSize(),
                request.getMaxSubscriptions()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to trigger subscription renewal job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchJobResponse.builder()
                    .success(false)
                    .message("Failed to trigger job: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Trigger email batch job.
     */
    @PostMapping("/email")
    public ResponseEntity<BatchJobResponse> triggerEmailBatch(@RequestBody BatchJobRequest request) {
        try {
            log.info("Received request to trigger email batch job for tenant: {}, templateId: {}",
                request.getTenantId(), request.getTemplateId());

            BatchJobResponse response = batchJobOrchestrationService.runEmailBatchJob(
                request.getTenantId(),
                request.getBatchSize(),
                request.getMaxEmails(),
                request.getTemplateId(),
                request.getRecipientEmails(),
                request.getUserId(),
                request.getRecipientType()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to trigger email batch job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchJobResponse.builder()
                    .success(false)
                    .message("Failed to trigger job: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Trigger contact form email batch job.
     * Processes contact form submissions and sends emails asynchronously.
     */
    @PostMapping("/contact-form-email")
    public ResponseEntity<ContactFormEmailJobResponse> triggerContactFormEmail(
        @Valid @RequestBody ContactFormEmailJobRequest request
    ) {
        try {
            log.info("Received request to trigger contact form email job - tenantId: {}, fromEmail: {}, toEmail: {}",
                request.getTenantId(),
                request.getFromEmail(),
                request.getToEmail()
            );

            ContactFormEmailJobResponse response = contactFormEmailJobService.triggerContactFormEmailJob(request);

            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.accepted().body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Failed to trigger contact form email job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ContactFormEmailJobResponse.builder()
                    .success(false)
                    .message("Failed to trigger job: " + e.getMessage())
                    .processedCount(0L)
                    .successCount(0L)
                    .failedCount(0L)
                    .build());
        }
    }

    /**
     * Trigger promotion test email job.
     * Sends a single email for a given promotion email template to a single recipient asynchronously.
     *
     * Mirrors the contact-form-email pattern: creates a BatchJobExecution and processes in the background.
     */
    @PostMapping("/promotion-test-email")
    public ResponseEntity<PromotionTestEmailJobResponse> triggerPromotionTestEmail(
        @Valid @RequestBody PromotionTestEmailJobRequest request
    ) {
        try {
            log.info(
                "Received request to trigger promotion test email job - tenantId: {}, templateId: {}, recipientEmail: {}",
                request.getTenantId(),
                request.getTemplateId(),
                request.getRecipientEmail()
            );

            PromotionTestEmailJobResponse response = promotionTestEmailJobService.triggerPromotionTestEmailJob(request);

            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.accepted().body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Failed to trigger promotion test email job: {}", e.getMessage(), e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    PromotionTestEmailJobResponse
                        .builder()
                        .success(false)
                        .message("Failed to trigger job: " + e.getMessage())
                        .processedCount(0L)
                        .successCount(0L)
                        .failedCount(0L)
                        .build()
                );
        }
    }

    /**
     * Trigger Stripe fees and tax update batch job.
     * Supports both on-demand and scheduled execution.
     */
    @PostMapping("/stripe-fees-tax-update")
    public ResponseEntity<StripeFeesTaxUpdateResponse> triggerStripeFeesTaxUpdate(
        @RequestBody(required = false) StripeFeesTaxUpdateRequest request
    ) {
        try {
            // Handle null request body (empty JSON)
            if (request == null) {
                request = new StripeFeesTaxUpdateRequest();
            }

            // Validate request
            if (request.getStartDate() != null && request.getEndDate() != null) {
                if (request.getStartDate().isAfter(request.getEndDate())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(StripeFeesTaxUpdateResponse.builder()
                            .status("FAILED")
                            .message("startDate must be before or equal to endDate")
                            .build());
                }
            }

            String tenantId = request.getTenantId();
            Long eventId = request.getEventId();
            ZonedDateTime startDate = request.getStartDate();
            ZonedDateTime endDate = request.getEndDate();
            boolean forceUpdate = request.getForceUpdate() != null ? request.getForceUpdate() : false;
            boolean useDefaultDateRange = request.getUseDefaultDateRange() != null ? request.getUseDefaultDateRange() : false;

            // If useDefaultDateRange is true, calculate dates automatically (14-day delay logic)
            ZonedDateTime calculatedStartDate = startDate;
            ZonedDateTime calculatedEndDate = endDate;
            if (useDefaultDateRange) {
                ZonedDateTime now = ZonedDateTime.now();
                calculatedStartDate = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                calculatedEndDate = now.minusDays(14).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            }

            // Provide default dates if null (to avoid PostgreSQL type inference issues)
            if (calculatedStartDate == null) {
                calculatedStartDate = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneId.systemDefault());
            }
            if (calculatedEndDate == null) {
                calculatedEndDate = ZonedDateTime.of(2099, 12, 31, 23, 59, 59, 999999999, java.time.ZoneId.systemDefault());
            }

            log.info("Received request to trigger Stripe fees and tax update job - tenantId: {}, eventId: {}, startDate: {}, endDate: {}, forceUpdate: {}, useDefaultDateRange: {}",
                tenantId, eventId, calculatedStartDate, calculatedEndDate, forceUpdate, useDefaultDateRange);

            // Convert ZonedDateTime to Timestamp for native query
            java.sql.Timestamp startTimestamp = java.sql.Timestamp.from(calculatedStartDate.toInstant());
            java.sql.Timestamp endTimestamp = java.sql.Timestamp.from(calculatedEndDate.toInstant());

            // Estimate number of records
            long estimatedRecords = 0;
            if (tenantId != null && !tenantId.isEmpty()) {
                estimatedRecords = transactionRepository.countTransactionsNeedingUpdate(
                    tenantId, eventId, forceUpdate, startTimestamp, endTimestamp
                );
            } else {
                // Estimate for all tenants (rough estimate)
                var tenantIds = transactionRepository.findDistinctTenantIds();
                for (String tid : tenantIds) {
                    estimatedRecords += transactionRepository.countTransactionsNeedingUpdate(
                        tid, eventId, forceUpdate, startTimestamp, endTimestamp
                    );
                }
            }

            // Generate job ID
            StringBuilder jobIdBuilder = new StringBuilder("stripe-fees-update-")
                .append(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            if (tenantId != null && !tenantId.isEmpty()) {
                jobIdBuilder.append("-").append(tenantId);
            }
            final String jobId = jobIdBuilder.toString();

            // Estimate completion time (rough estimate: 1 transaction per second with delays)
            final long finalEstimatedRecords = estimatedRecords;
            ZonedDateTime estimatedCompletion = ZonedDateTime.now().plusSeconds(finalEstimatedRecords * 2);

            // Start async processing
            stripeFeesTaxUpdateService.processStripeFeesAndTax(
                tenantId,
                eventId,
                calculatedStartDate,
                calculatedEndDate,
                forceUpdate,
                useDefaultDateRange
            )
                .thenAccept(stats -> {
                    log.info("Stripe fees and tax update job completed - jobId: {}, stats: {}", jobId, stats);
                })
                .exceptionally(ex -> {
                    log.error("Stripe fees and tax update job failed - jobId: {}", jobId, ex);
                    return null;
                });

            StripeFeesTaxUpdateResponse response = StripeFeesTaxUpdateResponse.builder()
                .jobId(jobId)
                .status("STARTED")
                .tenantId(tenantId)
                .startDate(calculatedStartDate)
                .endDate(calculatedEndDate)
                .forceUpdate(forceUpdate)
                .estimatedRecords(estimatedRecords)
                .estimatedCompletionTime(estimatedCompletion)
                .message("Batch job started successfully")
                .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("Failed to trigger Stripe fees and tax update job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StripeFeesTaxUpdateResponse.builder()
                    .status("FAILED")
                    .message("Failed to trigger job: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Batch Jobs Service is running");
    }
}




