package com.eventmanager.batch.job.subscription.reader;

import com.eventmanager.batch.domain.MembershipSubscription;
import com.eventmanager.batch.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * Reader for Subscription Renewal Batch Job.
 * Reads subscriptions that are approaching renewal date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalReader implements ItemReader<MembershipSubscription> {

    private final MembershipSubscriptionRepository repository;

    @Value("${batch.subscription-renewal.days-before-renewal:7}")
    private int daysBeforeRenewal;

    @Value("${batch.subscription-renewal.max-subscriptions:10000}")
    private int maxSubscriptions;

    private Iterator<MembershipSubscription> subscriptionIterator;
    private String currentTenantId;

    /**
     * Set the tenant ID to process. If null, processes all tenants.
     */
    public void setTenantId(String tenantId) {
        this.currentTenantId = tenantId;
        this.subscriptionIterator = null; // Reset iterator
    }

    @Override
    public MembershipSubscription read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (subscriptionIterator == null) {
            // Load subscriptions approaching renewal
            LocalDate checkDate = LocalDate.now().plusDays(daysBeforeRenewal);
            List<MembershipSubscription> subscriptions;

            if (currentTenantId != null && !currentTenantId.isEmpty()) {
                subscriptions = repository.findByTenantIdAndSubscriptionStatus(currentTenantId, "ACTIVE");
                // Filter by renewal date
                subscriptions = subscriptions.stream()
                    .filter(s -> s.getCurrentPeriodEnd() != null && s.getCurrentPeriodEnd().isBefore(checkDate) || s.getCurrentPeriodEnd().isEqual(checkDate))
                    .filter(s -> !Boolean.TRUE.equals(s.getCancelAtPeriodEnd()))
                    .filter(s -> s.getStripeSubscriptionId() != null && !s.getStripeSubscriptionId().isEmpty())
                    .limit(maxSubscriptions)
                    .toList();
            } else {
                subscriptions = repository.findSubscriptionsApproachingRenewal(checkDate);
                if (subscriptions.size() > maxSubscriptions) {
                    subscriptions = subscriptions.subList(0, maxSubscriptions);
                }
            }

            subscriptionIterator = subscriptions.iterator();
            log.info("Loaded {} subscriptions for renewal processing", subscriptions.size());
        }

        if (subscriptionIterator.hasNext()) {
            return subscriptionIterator.next();
        }

        return null; // End of data
    }
}




