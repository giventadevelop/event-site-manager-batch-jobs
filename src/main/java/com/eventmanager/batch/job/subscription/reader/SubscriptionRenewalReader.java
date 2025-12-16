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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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
    private String stripeSubscriptionId;

    /**
     * Set the tenant ID to process. Required for multi-tenant processing.
     * If null and stripeSubscriptionId is also null, will process all tenants (legacy behavior).
     */
    public void setTenantId(String tenantId) {
        this.currentTenantId = tenantId;
        this.subscriptionIterator = null; // Reset iterator
    }

    /**
     * Set the Stripe subscription ID to process. If provided, processes only this subscription.
     * When stripeSubscriptionId is provided, ALL filters are bypassed (7-day restriction, status checks, etc.).
     */
    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.subscriptionIterator = null; // Reset iterator
        log.debug("Set stripeSubscriptionId: {} (will bypass all filters)", stripeSubscriptionId);
    }

    @Override
    public MembershipSubscription read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (subscriptionIterator == null) {
            // Log current state for debugging
            log.info("SubscriptionRenewalReader.read() called - currentTenantId: {}, stripeSubscriptionId: {}, daysBeforeRenewal: {}",
                currentTenantId, stripeSubscriptionId, daysBeforeRenewal);

            // Load subscriptions approaching renewal
            LocalDate checkDate = LocalDate.now().plusDays(daysBeforeRenewal);
            List<MembershipSubscription> subscriptions;

            // If stripeSubscriptionId is provided, process only that subscription
            // BYPASS all filters (7-day restriction, status checks, etc.) when stripeSubscriptionId is provided
            if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
                log.info("Processing with stripeSubscriptionId: {}, currentTenantId: {} (bypassing all filters)",
                    stripeSubscriptionId, currentTenantId);

                if (currentTenantId != null && !currentTenantId.isEmpty()) {
                    log.debug("Looking up subscription: stripeSubscriptionId={}, tenantId={}",
                        stripeSubscriptionId, currentTenantId);
                    Optional<MembershipSubscription> subscription = repository
                        .findByStripeSubscriptionIdAndTenantId(stripeSubscriptionId, currentTenantId);

                    if (subscription.isPresent()) {
                        // When stripeSubscriptionId is provided, bypass all filters and return the subscription
                        // This allows testing/reprocessing subscriptions regardless of renewal date or status
                        MembershipSubscription sub = subscription.get();
                        subscriptions = Collections.singletonList(sub);
                        log.info("Found subscription by stripeSubscriptionId: {} for tenant: {} (bypassing all filters). " +
                                "Subscription ID: {}, Status: {}, PeriodEnd: {}, CancelAtPeriodEnd: {}",
                            stripeSubscriptionId, currentTenantId, sub.getId(), sub.getSubscriptionStatus(),
                            sub.getCurrentPeriodEnd(), sub.getCancelAtPeriodEnd());
                    } else {
                        subscriptions = Collections.emptyList();
                        log.warn("Subscription not found in database: stripeSubscriptionId={}, tenantId={}. " +
                                "Please verify the subscription exists and tenantId matches.",
                            stripeSubscriptionId, currentTenantId);
                    }
                } else {
                    // If tenantId is not provided, try to find by stripeSubscriptionId only
                    log.debug("Looking up subscription: stripeSubscriptionId={} (no tenantId filter)",
                        stripeSubscriptionId);
                    MembershipSubscription subscription = repository.findByStripeSubscriptionId(stripeSubscriptionId);
                    if (subscription != null) {
                        // When stripeSubscriptionId is provided, bypass all filters
                        subscriptions = Collections.singletonList(subscription);
                        log.info("Found subscription by stripeSubscriptionId: {} (bypassing all filters, no tenant filter). " +
                                "Subscription ID: {}, TenantId: {}, Status: {}, PeriodEnd: {}",
                            stripeSubscriptionId, subscription.getId(), subscription.getTenantId(),
                            subscription.getSubscriptionStatus(), subscription.getCurrentPeriodEnd());
                    } else {
                        subscriptions = Collections.emptyList();
                        log.warn("Subscription not found in database: stripeSubscriptionId={} (no tenantId provided). " +
                                "Please verify the subscription exists.",
                            stripeSubscriptionId);
                    }
                }
            } else if (currentTenantId != null && !currentTenantId.isEmpty()) {
                subscriptions = repository.findByTenantIdAndSubscriptionStatus(currentTenantId, "ACTIVE");
                // Filter by renewal date
                subscriptions = subscriptions.stream()
                    .filter(s -> s.getCurrentPeriodEnd() != null && s.getCurrentPeriodEnd().isBefore(checkDate) || s.getCurrentPeriodEnd().isEqual(checkDate))
                    .filter(s -> !Boolean.TRUE.equals(s.getCancelAtPeriodEnd()))
                    .filter(s -> s.getStripeSubscriptionId() != null && !s.getStripeSubscriptionId().isEmpty())
                    .limit(maxSubscriptions)
                    .toList();
            } else {
                // Legacy behavior: If no tenantId and no stripeSubscriptionId, process all tenants
                // Note: This should only happen in edge cases. Scheduled jobs should always provide tenantId.
                log.warn("No tenantId provided - processing subscriptions for all tenants. " +
                        "This is not recommended for multi-tenant systems.");
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







