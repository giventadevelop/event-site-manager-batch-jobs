package com.eventmanager.batch.job.donation.writer;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.repository.DonationTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * Writer for Donation QR Code Job.
 * Saves donation records with generated QR code URLs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationQrCodeItemWriter implements ItemWriter<DonationTransaction> {

    private final DonationTransactionRepository donationRepository;

    @Override
    public void write(Chunk<? extends DonationTransaction> chunk) throws Exception {
        for (DonationTransaction donation : chunk.getItems()) {
            try {
                log.debug("Saving QR code for donation: {}", donation.getId());

                donation.setUpdatedAt(ZonedDateTime.now());
                donationRepository.save(donation);

                log.info("Successfully saved QR code for donation: {}", donation.getId());

            } catch (Exception e) {
                log.error("Failed to save QR code for donation: {}", donation.getId(), e);
                throw e; // Re-throw to trigger retry
            }
        }
    }
}
