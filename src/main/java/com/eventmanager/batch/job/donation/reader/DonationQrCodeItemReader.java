package com.eventmanager.batch.job.donation.reader;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.repository.DonationTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * Reader for Donation QR Code Job.
 * Reads donations that need QR codes generated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationQrCodeItemReader implements ItemReader<DonationTransaction> {

    private final DonationTransactionRepository donationRepository;
    private Iterator<DonationTransaction> iterator;

    @Override
    public DonationTransaction read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (iterator == null) {
            // Find donations that need QR code:
            // - Status is COMPLETED
            // - event_id is not null
            // - qr_code_url is null
            List<DonationTransaction> donations = donationRepository.findDonationsNeedingQrCode();
            iterator = donations.iterator();
            log.info("Found {} donations needing QR code generation", donations.size());
        }

        if (iterator.hasNext()) {
            return iterator.next();
        }

        return null; // End of data
    }
}
