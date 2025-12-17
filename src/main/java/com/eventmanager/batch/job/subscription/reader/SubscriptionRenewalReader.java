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
 * Reads subscriptions that need renewal processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalReader implements ItemReader<MembershipSubscription> {

    private final MembershipSubscriptionRepository repository;

    @Value("${batch.subscription-renewal.renewal-days-ahead:7}")
    private int renewalDaysAhead;

    private Iterator<MembershipSubscription> subscriptionIterator;
    private String tenantId;
    private String stripeSubscriptionId;

    /**
     * Set the tenant ID for filtering subscriptions.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        this.subscriptionIterator = null; // Reset iterator when tenant changes
    }

    /**
     * Set the Stripe subscription ID for filtering a specific subscription.
     */
    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.subscriptionIterator = null; // Reset iterator when subscription ID changes
    }

    @Override
    public MembershipSubscription read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (subscriptionIterator == null) {
            List<MembershipSubscription> subscriptions = loadSubscriptions();
            if (subscriptions == null || subscriptions.isEmpty()) {
                log.info("No subscriptions found for renewal processing");
                return null;
            }
            subscriptionIterator = subscriptions.iterator();
            log.info("Loaded {} subscriptions for renewal processing", subscriptions.size());
        }

        if (subscriptionIterator.hasNext()) {
            return subscriptionIterator.next();
        }

        return null; // End of data
    }

    /**
     * Load subscriptions that need renewal processing.
     */
    private List<MembershipSubscription> loadSubscriptions() {
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("Tenant ID is not set, cannot load subscriptions");
            return List.of();
        }

        try {
            // If specific stripeSubscriptionId is provided, fetch only that subscription
            if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
                log.debug("Loading specific subscription: stripeSubscriptionId={}, tenantId={}",
                    stripeSubscriptionId, tenantId);
                return repository.findByStripeSubscriptionIdAndTenantId(stripeSubscriptionId, tenantId);
            }

            // Otherwise, load subscriptions that need renewal
            LocalDate renewalDateThreshold = LocalDate.now().plusDays(renewalDaysAhead);
            log.debug("Loading subscriptions needing renewal before: {}, tenantId={}",
                renewalDateThreshold, tenantId);
            return repository.findSubscriptionsNeedingRenewal(tenantId, renewalDateThreshold);
        } catch (Exception e) {
            log.error("Error loading subscriptions for tenant {}: {}", tenantId, e.getMessage(), e);
            return List.of();
        }
    }
}
