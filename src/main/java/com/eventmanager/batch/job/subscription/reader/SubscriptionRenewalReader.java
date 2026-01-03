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

    @Value("${batch.subscription-renewal.stripe-check-extended-days:30}")
    private int stripeCheckExtendedDays;

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
                log.info("Loading specific subscription - stripeSubscriptionId: {}, tenantId: {}",
                    stripeSubscriptionId, tenantId);
                List<MembershipSubscription> subscriptions = repository.findByStripeSubscriptionIdAndTenantId(stripeSubscriptionId, tenantId);

                // Detailed logging for specific subscription query
                log.info("Query executed with parameters: tenantId={}, stripeSubscriptionId={}",
                    tenantId, stripeSubscriptionId);
                log.info("Query results: Found {} subscription(s) matching criteria", subscriptions.size());

                if (!subscriptions.isEmpty()) {
                    log.debug("Subscription IDs found: {}",
                        subscriptions.stream()
                            .map(MembershipSubscription::getId)
                            .toList());
                } else {
                    log.warn("No subscription found with stripeSubscriptionId={} for tenant={}. " +
                            "Check: 1) Stripe subscription ID is correct, 2) Subscription exists for this tenant",
                        stripeSubscriptionId, tenantId);
                }

                return subscriptions;
            }

            // Otherwise, load subscriptions that need renewal
            LocalDate today = LocalDate.now();
            LocalDate renewalDateThreshold = today.plusDays(renewalDaysAhead);
            LocalDate extendedDateThreshold = today.plusDays(stripeCheckExtendedDays);

            // Detailed query logging with all parameters
            log.info("Loading subscriptions needing renewal - renewal window: {}, extended window for Stripe check: {}, tenantId={}",
                renewalDateThreshold, extendedDateThreshold, tenantId);
            log.info("Query executed with parameters: tenantId={}, renewalDateThreshold={}, extendedDateThreshold={}, " +
                    "renewalDaysAhead={}, stripeCheckExtendedDays={}, today={}",
                tenantId, renewalDateThreshold, extendedDateThreshold,
                renewalDaysAhead, stripeCheckExtendedDays, today);

            List<MembershipSubscription> subscriptions = repository.findSubscriptionsNeedingRenewal(
                tenantId, renewalDateThreshold, extendedDateThreshold);

            // Detailed logging of query results
            log.info("Query results: Found {} subscription(s) matching renewal criteria", subscriptions.size());

            if (!subscriptions.isEmpty()) {
                log.debug("Subscription IDs found: {}",
                    subscriptions.stream()
                        .map(MembershipSubscription::getId)
                        .toList());

                // Log summary statistics
                long withStripeId = subscriptions.stream()
                    .filter(s -> s.getStripeSubscriptionId() != null && !s.getStripeSubscriptionId().isEmpty())
                    .count();
                log.debug("Subscription breakdown: {} with Stripe ID, {} without Stripe ID",
                    withStripeId, subscriptions.size() - withStripeId);
            } else {
                log.warn("No subscriptions found matching renewal criteria for tenant: {}. " +
                        "Check: 1) Tenant ID is correct, 2) Subscriptions exist with ACTIVE/TRIALING status, " +
                        "3) Subscriptions are not cancelled (cancel_at_period_end=false), " +
                        "4) current_period_end is within renewal windows (standard: {} days, extended: {} days)",
                    tenantId, renewalDaysAhead, stripeCheckExtendedDays);
            }

            return subscriptions;
        } catch (Exception e) {
            log.error("Error loading subscriptions for tenant {}: {}", tenantId, e.getMessage(), e);
            return List.of();
        }
    }
}
