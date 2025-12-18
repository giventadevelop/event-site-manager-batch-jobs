package com.eventmanager.batch.controller;

import com.eventmanager.batch.dto.BatchJobRequest;
import com.eventmanager.batch.dto.BatchJobResponse;
import com.eventmanager.batch.dto.ContactFormEmailJobRequest;
import com.eventmanager.batch.dto.ContactFormEmailJobResponse;
import com.eventmanager.batch.service.BatchJobOrchestrationService;
import com.eventmanager.batch.service.ContactFormEmailJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final ContactFormEmailJobService contactFormEmailJobService;

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
     * Trigger contact form email job.
     * This accepts a single contact form submission and delegates asynchronous
     * email sending (main, copy-to, and confirmation) to the batch job service.
     */
    @PostMapping("/contact-form-email")
    public ResponseEntity<ContactFormEmailJobResponse> triggerContactFormEmailJob(
        @RequestBody ContactFormEmailJobRequest request
    ) {
        try {
            log.info("Received contact form email job request for tenant: {}", request.getTenantId());
            ContactFormEmailJobResponse response = contactFormEmailJobService.triggerContactFormEmailJob(request);
            HttpStatus status = Boolean.TRUE.equals(response.getSuccess())
                ? HttpStatus.OK
                : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            log.error("Failed to trigger contact form email job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ContactFormEmailJobResponse.builder()
                    .success(false)
                    .message("Failed to trigger contact form email job: " + e.getMessage())
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




