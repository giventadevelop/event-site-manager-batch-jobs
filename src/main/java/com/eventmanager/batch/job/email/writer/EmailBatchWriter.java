package com.eventmanager.batch.job.email.writer;

import com.eventmanager.batch.domain.PromotionEmailSentLog;
import com.eventmanager.batch.domain.enumeration.EmailStatus;
import com.eventmanager.batch.dto.EmailRecipient;
import com.eventmanager.batch.repository.PromotionEmailSentLogRepository;
import com.eventmanager.batch.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Writer for Email Batch Job.
 * Sends emails and logs the results.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailBatchWriter implements ItemWriter<EmailRecipient> {

    private final EmailService emailService;
    private final PromotionEmailSentLogRepository sentLogRepository;

    @Override
    public void write(Chunk<? extends EmailRecipient> chunk) throws Exception {
        List<? extends EmailRecipient> recipients = chunk.getItems();

        if (recipients.isEmpty()) {
            return;
        }

        // Send emails in batches (SES recommended batch size is 50)
        int batchSize = 50;
        for (int i = 0; i < recipients.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, recipients.size());
            List<? extends EmailRecipient> batch = recipients.subList(i, endIndex);

            // Extract emails for batch sending
            List<String> emailAddresses = batch.stream()
                .map(EmailRecipient::getEmail)
                .filter(email -> email != null && !email.isEmpty())
                .toList();

            if (emailAddresses.isEmpty()) {
                continue;
            }

            // Get common email properties from first recipient (all should be the same)
            EmailRecipient firstRecipient = batch.get(0);
            String fromEmail = firstRecipient.getFromEmail();
            String subject = firstRecipient.getSubject();
            String bodyHtml = firstRecipient.getBodyHtml();

            try {
                // Send batch email
                emailService.sendBatchEmails(fromEmail, emailAddresses, subject, bodyHtml, true);

                // Log successful emails
                for (EmailRecipient recipient : batch) {
                    logEmailSent(recipient, EmailStatus.SENT, null);
                }

                log.debug("Sent email batch {}/{}: {} emails", (i / batchSize + 1),
                    (int) Math.ceil((double) recipients.size() / batchSize), emailAddresses.size());
            } catch (Exception e) {
                log.error("Failed to send email batch: {}", e.getMessage(), e);

                // Log failed emails
                for (EmailRecipient recipient : batch) {
                    logEmailSent(recipient, EmailStatus.FAILED, e.getMessage());
                }
            }
        }

        log.info("Processed {} email recipients", recipients.size());
    }

    /**
     * Log email sent to database.
     */
    private void logEmailSent(EmailRecipient recipient, EmailStatus status, String errorMessage) {
        try {
            PromotionEmailSentLog log = new PromotionEmailSentLog();
            log.setTenantId(recipient.getTenantId());
            log.setTemplateId(recipient.getTemplateId());
            log.setEventId(recipient.getEventId());
            log.setRecipientEmail(recipient.getEmail());
            log.setSubject(recipient.getSubject());
            log.setPromotionCode(recipient.getPromotionCode());
            log.setDiscountCodeId(recipient.getDiscountCodeId());
            log.setSentAt(ZonedDateTime.now());
            log.setIsTestEmail(false);
            log.setEmailStatus(status);
            log.setErrorMessage(errorMessage);
            log.setSentById(recipient.getSentById());

            sentLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to log email sent for {}: {}", recipient.getEmail(), e.getMessage(), e);
            // Don't throw - logging failure shouldn't break email sending
        }
    }
}

