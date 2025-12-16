package com.eventmanager.batch.job.subscription.processor;

import com.eventmanager.batch.domain.MembershipSubscription;
import com.eventmanager.batch.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Processor for Subscription Renewal Batch Job.
 * Fetches latest subscription data from Stripe and updates local record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalProcessor implements ItemProcessor<MembershipSubscription, MembershipSubscription> {

    private final StripeService stripeService;
    private String currentTenantId;
    private String stripeSubscriptionId;

    // Cache to track last initialized tenant to avoid repeated DB reads and decryption
    private String lastInitializedTenantId;

    /**
     * Set the tenant ID for Stripe initialization.
     * This will trigger Stripe initialization once before processing subscriptions.
     */
    public void setTenantId(String tenantId) {
        this.currentTenantId = tenantId;
        // Reset last initialized tenant to force initialization on first subscription
        this.lastInitializedTenantId = null;
    }

    /**
     * Set the Stripe subscription ID. Used for logging and tracking purposes.
     */
    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    /**
     * Initialize Stripe for tenant if not already initialized.
     * This ensures we only read payment provider config and decrypt keys once per tenant.
     */
    private void ensureStripeInitialized(String tenantId) {
        // Only initialize if tenant changed or not yet initialized
        if (lastInitializedTenantId == null || !lastInitializedTenantId.equals(tenantId)) {
            log.debug("Initializing Stripe for tenant: {} (previous tenant: {})",
                tenantId, lastInitializedTenantId);
            stripeService.initStripe(tenantId);
            lastInitializedTenantId = tenantId;
            log.debug("Stripe initialized successfully for tenant: {}", tenantId);
        } else {
            log.trace("Stripe already initialized for tenant: {}, skipping re-initialization", tenantId);
        }
    }

    @Override
    public MembershipSubscription process(MembershipSubscription subscription) throws Exception {
        if (subscription.getStripeSubscriptionId() == null || subscription.getStripeSubscriptionId().isEmpty()) {
            log.warn("Subscription {} has no Stripe subscription ID, skipping", subscription.getId());
            return null; // Skip this item
        }

        try {
            // Determine tenant ID to use
            String tenantIdToUse = (currentTenantId != null && !currentTenantId.isEmpty())
                ? currentTenantId
                : subscription.getTenantId();

            // Initialize Stripe for tenant (only once per tenant - cached)
            ensureStripeInitialized(tenantIdToUse);

            // Fetch latest subscription data from Stripe
            Subscription stripeSubscription = stripeService.retrieveSubscription(subscription.getStripeSubscriptionId());

            // Update local subscription with Stripe data
            subscription.setCurrentPeriodStart(convertToLocalDate(stripeSubscription.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(convertToLocalDate(stripeSubscription.getCurrentPeriodEnd()));
            subscription.setSubscriptionStatus(stripeService.mapStripeStatus(stripeSubscription.getStatus()));
            subscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()));

            if (stripeSubscription.getCanceledAt() != null) {
                subscription.setCancelledAt(ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCanceledAt()),
                    ZoneId.systemDefault()));
            }

            subscription.setLastStripeSyncAt(ZonedDateTime.now());
            subscription.setLastReconciliationAt(ZonedDateTime.now());
            subscription.setReconciliationStatus("SUCCESS");
            subscription.setReconciliationError(null);
            subscription.setUpdatedAt(ZonedDateTime.now());

            log.debug("Processed subscription {} - Status: {}, Period End: {}",
                subscription.getId(), subscription.getSubscriptionStatus(), subscription.getCurrentPeriodEnd());

            return subscription;
        } catch (StripeException e) {
            log.error("Failed to process subscription {}: {}", subscription.getId(), e.getMessage(), e);
            subscription.setReconciliationStatus("FAILED");
            subscription.setReconciliationError(e.getMessage());
            subscription.setLastReconciliationAt(ZonedDateTime.now());
            return subscription; // Return with error status for writer to handle
        } catch (Exception e) {
            log.error("Unexpected error processing subscription {}: {}", subscription.getId(), e.getMessage(), e);
            subscription.setReconciliationStatus("FAILED");
            subscription.setReconciliationError(e.getMessage());
            subscription.setLastReconciliationAt(ZonedDateTime.now());
            return subscription;
        }
    }

    private LocalDate convertToLocalDate(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}







