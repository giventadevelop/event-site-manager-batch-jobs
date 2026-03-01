package com.eventmanager.batch.job.donation.processor;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Processor for Donation QR Code Job.
 * Generates QR code URLs for donations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationQrCodeItemProcessor implements ItemProcessor<DonationTransaction, DonationTransaction> {

    private final QrCodeService qrCodeService;

    @Override
    public DonationTransaction process(DonationTransaction donation) throws Exception {
        try {
            log.debug("Generating QR code for donation: {}", donation.getId());

            if (donation.getEventId() == null) {
                log.warn("Donation {} has no eventId, skipping QR code generation", donation.getId());
                return donation;
            }

            String emailHostUrlPrefix = qrCodeService.getEmailHostUrlPrefix();
            String qrCodeUrl = qrCodeService.generateQrCode(
                donation.getEventId(),
                donation.getId(),
                emailHostUrlPrefix,
                "DONATION"
            );

            String qrCodeImageUrl = qrCodeService.generateQrCodeImageUrl(qrCodeUrl);

            donation.setQrCodeUrl(qrCodeUrl);
            donation.setQrCodeImageUrl(qrCodeImageUrl);

            log.info("Generated QR code for donation {}: {}", donation.getId(), qrCodeUrl);
            return donation;

        } catch (Exception e) {
            log.error("Failed to generate QR code for donation: {}", donation.getId(), e);
            throw e; // Re-throw to trigger retry
        }
    }
}
