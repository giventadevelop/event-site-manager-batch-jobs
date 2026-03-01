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
 * Reader for Donation Email Job.
 * Reads donations that need confirmation emails sent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationEmailItemReader implements ItemReader<DonationTransaction> {

    private final DonationTransactionRepository donationRepository;
    private Iterator<DonationTransaction> iterator;

    @Override
    public DonationTransaction read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (iterator == null) {
            // Find donations that need email sent:
            // - Status is COMPLETED
            // - Email is not null
            // - email_sent is false
            List<DonationTransaction> donations = donationRepository.findDonationsNeedingEmail();
            iterator = donations.iterator();
            log.info("Found {} donations needing email confirmation", donations.size());
        }

        if (iterator.hasNext()) {
            return iterator.next();
        }

        return null; // End of data
    }
}
