package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import com.eventmanager.batch.dto.ContactFormEmailJobRequest;
import com.eventmanager.batch.dto.ContactFormEmailJobResponse;
import com.eventmanager.batch.repository.TenantEmailAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for handling contact form email jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactFormEmailJobService {

    private final BatchJobExecutionService batchJobExecutionService;
    private final TenantEmailAddressRepository tenantEmailAddressRepository;
    private final EmailContentBuilderService emailContentBuilderService;
    private final EmailService emailService;

    /**
     * Entry point used by REST controller to trigger a contact form email job.
     * Creates a job execution record and starts asynchronous processing.
     */
    public ContactFormEmailJobResponse triggerContactFormEmailJob(ContactFormEmailJobRequest request) {
        // Basic validation (Bean Validation will handle most, but double-check tenantId)
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ContactFormEmailJobResponse.builder()
                .success(false)
                .message("Tenant ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        String parametersJson = String.format(
            "{\"tenantId\":\"%s\",\"fromEmail\":\"%s\",\"toEmail\":\"%s\"}",
            request.getTenantId(),
            request.getFromEmail(),
            request.getToEmail()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "contactFormEmailJob",
            "CONTACT_FORM_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        // Fire-and-forget async processing
        processContactFormEmailAsync(execution.getId(), request);

        return ContactFormEmailJobResponse.builder()
            .success(true)
            .message("Contact form email job accepted for processing")
            .jobExecutionId(execution.getId())
            .processedCount(0L)
            .successCount(0L)
            .failedCount(0L)
            .build();
    }

    /**
     * Asynchronous handler that performs the actual email sending.
     */
    @Async
    protected void processContactFormEmailAsync(Long executionId, ContactFormEmailJobRequest request) {
        long processedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;

        try {
            String tenantId = request.getTenantId();

            String fromAddress = resolveContactFromEmail(tenantId);
            if (fromAddress == null || fromAddress.isEmpty()) {
                log.warn("No FROM address resolved for tenant: {}, aborting contact form email", tenantId);
                failedCount = 1L;
                batchJobExecutionService.completeJobExecution(
                    executionId,
                    "FAILED",
                    processedCount,
                    successCount,
                    failedCount,
                    "No FROM address configured for tenant"
                );
                return;
            }

            String copyToAddress = resolveContactCopyToEmail(tenantId);

            String subject = String.format(
                "Contact Form Submission from %s %s",
                request.getFirstName(),
                request.getLastName()
            );

            String mainBody = emailContentBuilderService.buildContactEmailBody(request);
            String confirmationBody = emailContentBuilderService.buildContactConfirmationEmailBody(request);

            processedCount = 1L;

            // Main email to tenant TO address with Reply-To set to visitor email
            emailService.sendEmail(
                fromAddress,
                request.getFromEmail(),
                request.getToEmail(),
                subject,
                mainBody,
                true
            );

            // Copy email if configured
            if (copyToAddress != null && !copyToAddress.isBlank()) {
                emailService.sendEmail(
                    fromAddress,
                    request.getFromEmail(),
                    copyToAddress,
                    subject + " (Copy)",
                    mainBody,
                    true
                );
            }

            // Confirmation email to visitor (no Reply-To required)
            emailService.sendEmail(
                fromAddress,
                null,
                request.getFromEmail(),
                "We received your message",
                confirmationBody,
                true
            );

            successCount = 1L;
            batchJobExecutionService.completeJobExecution(
                executionId,
                "COMPLETED",
                processedCount,
                successCount,
                failedCount,
                null
            );
        } catch (Exception e) {
            log.error("Failed to process contact form email job execution {}: {}", executionId, e.getMessage(), e);
            failedCount = 1L;
            batchJobExecutionService.completeJobExecution(
                executionId,
                "FAILED",
                processedCount,
                successCount,
                failedCount,
                e.getMessage()
            );
        }
    }

    /**
     * Resolve FROM address for contact form emails for a tenant.
     * Prefers CONTACT type, then default, then any active email address.
     */
    @Cacheable(cacheNames = "tenantEmailFromCache", key = "#tenantId", unless = "#result == null || #result.isEmpty()")
    public String resolveContactFromEmail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        // 1. Prefer CONTACT type, active and default first
        Optional<TenantEmailAddress> preferredContact = tenantEmailAddressRepository
            .findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(tenantId, TenantEmailType.CONTACT);

        if (preferredContact.isPresent()) {
            String value = preferredContact.get().getEmailAddress();
            return value;
        }

        // 2. Fall back to default active email
        Optional<TenantEmailAddress> defaultEmail = tenantEmailAddressRepository
            .findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(tenantId);
        if (defaultEmail.isPresent()) {
            String value = defaultEmail.get().getEmailAddress();
            return value;
        }

        // 3. Fall back to any active email
        List<TenantEmailAddress> activeEmails = tenantEmailAddressRepository
            .findByTenantIdAndIsActiveTrue(tenantId);
        if (!activeEmails.isEmpty()) {
            String value = activeEmails.get(0).getEmailAddress();
            return value;
        }

        log.warn("No active tenant email address found for tenant: {}", tenantId);
        return "";
    }

    /**
     * Resolve COPY-TO address for contact form emails for a tenant.
     * Uses the same selection algorithm as resolveContactFromEmail, but
     * returns the copyToEmailAddress field if configured.
     */
    @Cacheable(cacheNames = "tenantEmailCopyToCache", key = "#tenantId", unless = "#result == null || #result.isEmpty()")
    public String resolveContactCopyToEmail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        // 1. Prefer CONTACT type, active and default first
        Optional<TenantEmailAddress> preferredContact = tenantEmailAddressRepository
            .findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(tenantId, TenantEmailType.CONTACT);

        Optional<TenantEmailAddress> selected = preferredContact;

        // 2. Fall back to default active email
        if (selected.isEmpty()) {
            selected = tenantEmailAddressRepository
                .findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(tenantId);
        }

        // 3. Fall back to any active email
        if (selected.isEmpty()) {
            List<TenantEmailAddress> activeEmails = tenantEmailAddressRepository
                .findByTenantIdAndIsActiveTrue(tenantId);
            if (!activeEmails.isEmpty()) {
                selected = Optional.of(activeEmails.get(0));
            }
        }

        if (selected.isPresent()) {
            String copyTo = selected.get().getCopyToEmailAddress();
            if (copyTo != null && !copyTo.isBlank()) {
                return copyTo;
            }
        }

        return "";
    }
}


