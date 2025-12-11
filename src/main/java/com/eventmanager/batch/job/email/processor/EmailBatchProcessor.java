package com.eventmanager.batch.job.email.processor;

import com.eventmanager.batch.dto.EmailRecipient;
import com.eventmanager.batch.service.EmailContentBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Processor for Email Batch Job.
 * Builds email content for each recipient.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailBatchProcessor implements ItemProcessor<EmailRecipient, EmailRecipient> {

    private final EmailContentBuilderService emailContentBuilderService;
    private com.eventmanager.batch.domain.PromotionEmailTemplate template;

    /**
     * Set the template for building email content.
     */
    public void setTemplate(com.eventmanager.batch.domain.PromotionEmailTemplate template) {
        this.template = template;
    }

    @Override
    public EmailRecipient process(EmailRecipient recipient) throws Exception {
        if (template == null) {
            log.error("Template not set in processor");
            return null;
        }

        try {
            // Build email content
            Map<String, String> emailContent = emailContentBuilderService.buildEmailContent(template);

            // Set subject and body HTML
            recipient.setSubject(emailContent.get("subject"));
            recipient.setBodyHtml(emailContent.get("bodyHtml"));

            log.debug("Processed email recipient: {}", recipient.getEmail());
            return recipient;
        } catch (Exception e) {
            log.error("Failed to process email recipient {}: {}", recipient.getEmail(), e.getMessage(), e);
            return null; // Skip this recipient
        }
    }
}

