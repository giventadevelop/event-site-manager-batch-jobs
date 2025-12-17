package com.eventmanager.batch.job.subscription.processor;

import com.eventmanager.batch.domain.MembershipSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * Processor for Subscription Renewal Batch Job.
 * Processes subscriptions and syncs with Stripe if needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalProcessor implements ItemProcessor<MembershipSubscription, MembershipSubscription> {

    private String tenantId;
    private String stripeSubscriptionId;

    /**
     * Set the tenant ID for processing.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Set the Stripe subscription ID for processing a specific subscription.
     */
    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    @Override
    public MembershipSubscription process(MembershipSubscription subscription) throws Exception {
        if (subscription == null) {
            log.warn("Received null subscription, skipping");
            return null;
        }

        try {
            log.debug("Processing subscription: id={}, stripeSubscriptionId={}, tenantId={}",
                subscription.getId(), subscription.getStripeSubscriptionId(), subscription.getTenantId());

            // TODO: Add Stripe sync logic here
            // For now, just update the last reconciliation timestamp
            subscription.setLastReconciliationAt(ZonedDateTime.now());
            subscription.setLastStripeSyncAt(ZonedDateTime.now());
            subscription.setReconciliationStatus("PROCESSED");

            log.debug("Processed subscription: id={}", subscription.getId());
            return subscription;
        } catch (Exception e) {
            log.error("Failed to process subscription {}: {}", subscription.getId(), e.getMessage(), e);
            subscription.setReconciliationStatus("ERROR");
            subscription.setReconciliationError(e.getMessage());
            // Return the subscription with error status so it can be logged
            return subscription;
        }
    }
}
