package com.eventmanager.batch.job.email.reader;

import com.eventmanager.batch.domain.EventAttendee;
import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.domain.UserProfile;
import com.eventmanager.batch.dto.EmailRecipient;
import com.eventmanager.batch.repository.EventAttendeeRepository;
import com.eventmanager.batch.repository.PromotionEmailTemplateRepository;
import com.eventmanager.batch.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reader for Email Batch Job.
 * Reads recipient emails based on template configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailBatchReader implements ItemReader<EmailRecipient> {

    private final PromotionEmailTemplateRepository templateRepository;
    private final EventAttendeeRepository eventAttendeeRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${batch.email.max-emails:10000}")
    private int maxEmails;

    private Iterator<EmailRecipient> recipientIterator;
    private PromotionEmailTemplate template;
    private List<String> recipientEmails;
    private String tenantId;
    private Long userId;
    private String recipientType; // "EVENT_ATTENDEES" or "SUBSCRIBED_MEMBERS"

    /**
     * Initialize reader with job parameters.
     */
    public void initialize(Long templateId, String tenantId, List<String> recipientEmails, Long userId, Integer maxEmails, String recipientType) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.recipientType = recipientType;
        this.recipientIterator = null; // Reset iterator

        if (maxEmails != null && maxEmails > 0) {
            this.maxEmails = maxEmails;
        }

        // Load template
        if (templateId == null) {
            throw new IllegalArgumentException("Template ID is required");
        }

        this.template = templateRepository.findByIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        // Get recipient emails if not provided
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            this.recipientEmails = getRecipientEmailsFromDatabase();
        } else {
            this.recipientEmails = recipientEmails.stream()
                .distinct()
                .limit(this.maxEmails)
                .collect(Collectors.toList());
        }

        log.info("Initialized email batch reader: templateId={}, tenantId={}, recipientCount={}",
            templateId, tenantId, this.recipientEmails.size());
    }

    @Override
    public EmailRecipient read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (recipientIterator == null) {
            if (template == null || recipientEmails == null || recipientEmails.isEmpty()) {
                log.warn("No recipients to process");
                return null;
            }

            // Create EmailRecipient objects for each email
            // Use tenantId from request (not template.getTenantId()) to ensure correct tenant context
            List<EmailRecipient> recipients = recipientEmails.stream()
                .map(email -> EmailRecipient.builder()
                    .email(email)
                    .templateId(template.getId())
                    .tenantId(tenantId) // Use tenantId from request for fallback lookups
                    .eventId(template.getEventId())
                    .fromEmail(template.getFromEmail())
                    .promotionCode(template.getPromotionCode())
                    .discountCodeId(template.getDiscountCodeId())
                    .sentById(userId)
                    .build())
                .collect(Collectors.toList());

            recipientIterator = recipients.iterator();
            log.info("Created {} email recipient objects", recipients.size());
        }

        if (recipientIterator.hasNext()) {
            return recipientIterator.next();
        }

        return null; // End of data
    }

    /**
     * Get recipient emails from database based on recipientType or template configuration.
     *
     * Priority:
     * 1. If recipientType is explicitly set, use it
     * 2. Otherwise, infer from template.eventId:
     *    - If template has eventId → EVENT_ATTENDEES
     *    - If template has no eventId → SUBSCRIBED_MEMBERS
     *
     * @return List of recipient email addresses
     */
    private List<String> getRecipientEmailsFromDatabase() {
        String effectiveRecipientType = recipientType;

        // If recipientType not explicitly set, infer from template
        if (effectiveRecipientType == null || effectiveRecipientType.isEmpty()) {
            if (template.getEventId() != null) {
                effectiveRecipientType = "EVENT_ATTENDEES";
                log.debug("Recipient type not specified, inferred as EVENT_ATTENDEES from template.eventId: {}", template.getEventId());
            } else {
                effectiveRecipientType = "SUBSCRIBED_MEMBERS";
                log.debug("Recipient type not specified, inferred as SUBSCRIBED_MEMBERS (template has no eventId)");
            }
        }

        // Get emails based on effective recipient type
        List<String> emails;
        if ("EVENT_ATTENDEES".equalsIgnoreCase(effectiveRecipientType)) {
            if (template.getEventId() == null) {
                log.warn("Recipient type is EVENT_ATTENDEES but template has no eventId. Cannot fetch event attendees.");
                return List.of();
            }
            emails = eventAttendeeRepository.findConfirmedEmailsByEventId(template.getEventId());
            log.info("Retrieved {} recipient emails from event attendees for eventId: {}", emails.size(), template.getEventId());
        } else if ("SUBSCRIBED_MEMBERS".equalsIgnoreCase(effectiveRecipientType)) {
            emails = userProfileRepository.findSubscribedEmailsByTenantId(tenantId);
            log.info("Retrieved {} recipient emails from subscribed members for tenantId: {}", emails.size(), tenantId);
        } else {
            log.error("Invalid recipientType: {}. Expected 'EVENT_ATTENDEES' or 'SUBSCRIBED_MEMBERS'", effectiveRecipientType);
            return List.of();
        }

        return emails.stream()
            .distinct()
            .limit(maxEmails)
            .collect(Collectors.toList());
    }

    /**
     * Get the template for building email content.
     */
    public PromotionEmailTemplate getTemplate() {
        return template;
    }
}

