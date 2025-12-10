package com.eventmanager.batch.job.subscription.writer;

import com.eventmanager.batch.domain.MembershipSubscription;
import com.eventmanager.batch.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Writer for Subscription Renewal Batch Job.
 * Saves updated subscription records to database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalWriter implements ItemWriter<MembershipSubscription> {

    private final MembershipSubscriptionRepository repository;

    @Override
    public void write(Chunk<? extends MembershipSubscription> chunk) throws Exception {
        List<? extends MembershipSubscription> subscriptions = chunk.getItems();

        for (MembershipSubscription subscription : subscriptions) {
            try {
                repository.save(subscription);
                log.debug("Saved subscription {} with status {}", subscription.getId(), subscription.getSubscriptionStatus());
            } catch (Exception e) {
                log.error("Failed to save subscription {}: {}", subscription.getId(), e.getMessage(), e);
                // Continue processing other subscriptions
            }
        }

        log.info("Saved {} subscription records", subscriptions.size());
    }
}


