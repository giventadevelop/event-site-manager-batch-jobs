package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.domain.PromotionEmailSentLog;
import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.domain.enumeration.EmailStatus;
import com.eventmanager.batch.dto.PromotionTestEmailJobRequest;
import com.eventmanager.batch.dto.PromotionTestEmailJobResponse;
import com.eventmanager.batch.repository.PromotionEmailSentLogRepository;
import com.eventmanager.batch.repository.PromotionEmailTemplateRepository;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service responsible for handling promotion test email jobs.
 *
 * Pattern mirrors {@link ContactFormEmailJobService}: create BatchJobExecution, fire async processing,
 * and return an ACCEPTED response immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionTestEmailJobService {

    private final BatchJobExecutionService batchJobExecutionService;
    private final PromotionEmailTemplateRepository promotionEmailTemplateRepository;
    private final PromotionEmailSentLogRepository promotionEmailSentLogRepository;
    private final EmailContentBuilderService emailContentBuilderService;
    private final EmailService emailService;

    public PromotionTestEmailJobResponse triggerPromotionTestEmailJob(PromotionTestEmailJobRequest request) {
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return PromotionTestEmailJobResponse
                .builder()
                .success(false)
                .message("Tenant ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }
        if (request.getTemplateId() == null) {
            return PromotionTestEmailJobResponse
                .builder()
                .success(false)
                .message("Template ID is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }
        if (request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()) {
            return PromotionTestEmailJobResponse
                .builder()
                .success(false)
                .message("Recipient email is required")
                .processedCount(0L)
                .successCount(0L)
                .failedCount(0L)
                .build();
        }

        String parametersJson = String.format(
            "{\"tenantId\":\"%s\",\"templateId\":%d,\"recipientEmail\":\"%s\"}",
            request.getTenantId(),
            request.getTemplateId(),
            request.getRecipientEmail()
        );

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "promotionTestEmailJob",
            "PROMOTION_TEST_EMAIL",
            request.getTenantId(),
            "API",
            parametersJson
        );

        processPromotionTestEmailAsync(execution.getId(), request);

        return PromotionTestEmailJobResponse
            .builder()
            .success(true)
            .message("Promotion test email job accepted for processing")
            .jobExecutionId(execution.getId())
            .processedCount(0L)
            .successCount(0L)
            .failedCount(0L)
            .build();
    }

    @Async
    protected void processPromotionTestEmailAsync(Long executionId, PromotionTestEmailJobRequest request) {
        long processedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;

        try {
            String tenantId = request.getTenantId();
            Long templateId = request.getTemplateId();

            PromotionEmailTemplate template = promotionEmailTemplateRepository
                .findByIdAndTenantId(templateId, tenantId)
                .orElse(null);

            if (template == null) {
                failedCount = 1L;
                batchJobExecutionService.completeJobExecution(
                    executionId,
                    "FAILED",
                    processedCount,
                    successCount,
                    failedCount,
                    "Template not found for tenant: templateId=" + templateId + ", tenantId=" + tenantId
                );
                return;
            }

            // Ensure header/footer assets are ready (same idea as email batch job).
            emailContentBuilderService.ensureHeaderAndFooterReady(tenantId, 10);

            Map<String, String> emailContent = emailContentBuilderService.buildEmailContent(template, null, null, tenantId);
            String subject = emailContent.get("subject");
            String bodyHtml = emailContent.get("bodyHtml");

            processedCount = 1L;
            emailService.sendEmail(template.getFromEmail(), null, request.getRecipientEmail(), subject, bodyHtml, true);

            successCount = 1L;
            batchJobExecutionService.completeJobExecution(
                executionId,
                "COMPLETED",
                processedCount,
                successCount,
                failedCount,
                null
            );

            logPromotionEmailSent(template, request, subject, EmailStatus.SENT, null);
        } catch (Exception e) {
            log.error("Failed to process promotion test email job execution {}: {}", executionId, e.getMessage(), e);
            failedCount = 1L;
            batchJobExecutionService.completeJobExecution(executionId, "FAILED", processedCount, successCount, failedCount, e.getMessage());

            try {
                PromotionEmailTemplate template = promotionEmailTemplateRepository
                    .findByIdAndTenantId(request.getTemplateId(), request.getTenantId())
                    .orElse(null);
                if (template != null) {
                    logPromotionEmailSent(template, request, template.getSubject(), EmailStatus.FAILED, e.getMessage());
                }
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    private void logPromotionEmailSent(
        PromotionEmailTemplate template,
        PromotionTestEmailJobRequest request,
        String subject,
        EmailStatus status,
        String errorMessage
    ) {
        try {
            PromotionEmailSentLog logRow = new PromotionEmailSentLog();
            logRow.setTenantId(request.getTenantId());
            logRow.setTemplateId(template.getId());
            logRow.setEventId(template.getEventId()); // may be null for newsletters
            logRow.setRecipientEmail(request.getRecipientEmail());
            logRow.setSubject(subject);
            logRow.setPromotionCode(template.getPromotionCode());
            logRow.setDiscountCodeId(template.getDiscountCodeId());
            logRow.setSentAt(ZonedDateTime.now());
            logRow.setIsTestEmail(true);
            logRow.setEmailStatus(status);
            logRow.setErrorMessage(errorMessage);
            logRow.setSentById(request.getUserId());
            promotionEmailSentLogRepository.save(logRow);
        } catch (Exception e) {
            log.error("Failed to log promotion test email sent: {}", e.getMessage(), e);
        }
    }
}

