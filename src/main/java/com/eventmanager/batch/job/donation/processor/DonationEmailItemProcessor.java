package com.eventmanager.batch.job.donation.processor;

import com.eventmanager.batch.domain.DonationTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Processor for Donation Email Job.
 * Passes through donation transactions (no transformation needed).
 */
@Component
@Slf4j
public class DonationEmailItemProcessor implements ItemProcessor<DonationTransaction, DonationTransaction> {

    @Override
    public DonationTransaction process(DonationTransaction donation) throws Exception {
        log.debug("Processing donation {} for email sending", donation.getId());
        return donation;
    }
}
