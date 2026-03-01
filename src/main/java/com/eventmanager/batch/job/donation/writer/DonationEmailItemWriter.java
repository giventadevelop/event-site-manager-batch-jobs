package com.eventmanager.batch.job.donation.writer;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.repository.DonationTransactionRepository;
import com.eventmanager.batch.service.EmailContentBuilderService;
import com.eventmanager.batch.service.EmailService;
import com.eventmanager.batch.service.BackendApiService;
import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import com.eventmanager.batch.repository.TenantEmailAddressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * Writer for Donation Email Job.
 * Sends donation confirmation emails and updates donation records.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationEmailItemWriter implements ItemWriter<DonationTransaction> {

    private final EmailService emailService;
    private final EmailContentBuilderService emailContentBuilderService;
    private final DonationTransactionRepository donationRepository;
    private final BackendApiService backendApiService;
    private final TenantEmailAddressRepository tenantEmailAddressRepository;

    @Override
    public void write(Chunk<? extends DonationTransaction> chunk) throws Exception {
        for (DonationTransaction donation : chunk.getItems()) {
            try {
                log.debug("Sending confirmation email for donation: {}", donation.getId());

                // Fetch event details from backend if eventId is present
                String eventTitle = null;
                if (donation.getEventId() != null) {
                    JsonNode eventDetails = backendApiService.getEventDetails(donation.getEventId());
                    if (eventDetails != null && eventDetails.has("title")) {
                        eventTitle = eventDetails.get("title").asText();
                    }
                }

                // Build email content
                String subject = String.format(
                    "Thank you for your donation%s",
                    eventTitle != null ? " - " + eventTitle : ""
                );

                String bodyHtml = emailContentBuilderService.buildDonationConfirmationEmailBody(
                    donation.getEventId(),
                    donation.getId(),
                    donation.getTransactionReference(),
                    donation.getAmount(),
                    donation.getEmail(),
                    donation.getFirstName(),
                    donation.getLastName(),
                    eventTitle,
                    donation.getQrCodeImageUrl(),
                    donation.getTenantId()
                );

                // Resolve FROM address (using tenant email address)
                String fromAddress = resolveFromEmail(donation.getTenantId());
                if (fromAddress == null || fromAddress.isEmpty()) {
                    log.warn("No FROM address found for tenant: {}, skipping email for donation: {}",
                        donation.getTenantId(), donation.getId());
                    continue;
                }

                // Send email
                emailService.sendEmail(
                    fromAddress,
                    null,
                    donation.getEmail(),
                    subject,
                    bodyHtml,
                    true
                );

                // Mark email as sent
                donation.setEmailSent(true);
                donation.setUpdatedAt(ZonedDateTime.now());
                donationRepository.save(donation);

                log.info("Successfully sent confirmation email for donation: {}", donation.getId());

            } catch (Exception e) {
                log.error("Failed to send email for donation: {}", donation.getId(), e);
                // Don't throw - continue processing other donations
                // The job will track failures through batch job execution log
            }
        }
    }

    /**
     * Resolve FROM address for donation emails for a tenant.
     * Prefers CONTACT type, then default, then any active email address.
     */
    private String resolveFromEmail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }

        // 1. Prefer CONTACT type, active and default first
        var preferredContact = tenantEmailAddressRepository
            .findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(tenantId, TenantEmailType.CONTACT);

        if (preferredContact.isPresent()) {
            return preferredContact.get().getEmailAddress();
        }

        // 2. Fall back to default active email
        var defaultEmail = tenantEmailAddressRepository
            .findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(tenantId);
        if (defaultEmail.isPresent()) {
            return defaultEmail.get().getEmailAddress();
        }

        // 3. Fall back to any active email
        var activeEmails = tenantEmailAddressRepository
            .findByTenantIdAndIsActiveTrue(tenantId);
        if (!activeEmails.isEmpty()) {
            return activeEmails.get(0).getEmailAddress();
        }

        log.warn("No active tenant email address found for tenant: {}", tenantId);
        return "";
    }
}
