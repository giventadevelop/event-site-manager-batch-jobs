package com.eventmanager.batch.job.subscription.processor;

import com.eventmanager.batch.domain.MembershipSubscription;
import com.eventmanager.batch.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Processor for Subscription Renewal Batch Job.
 * Processes subscriptions and syncs with Stripe if needed.
 * Checks Stripe dates when subscription has stripe_subscription_id to handle
 * cases where Stripe dates are advanced but database is not yet updated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalProcessor implements ItemProcessor<MembershipSubscription, MembershipSubscription> {

    private final StripeService stripeService;
    private final Environment environment;

    @Value("${batch.subscription-renewal.renewal-days-ahead:7}")
    private int renewalDaysAhead;

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
            log.debug("[SUBSCRIPTION-RENEWAL] Processing subscription ID: {}, Stripe ID: {}, Tenant: {}",
                subscription.getId(), subscription.getStripeSubscriptionId(), subscription.getTenantId());

            // If subscription has Stripe subscription ID, check Stripe dates
            if (subscription.getStripeSubscriptionId() != null && !subscription.getStripeSubscriptionId().isEmpty()) {
                return processWithStripeCheck(subscription);
            } else {
                // No Stripe subscription ID - use database date check (existing logic)
                return processWithoutStripeCheck(subscription);
            }
        } catch (Exception e) {
            log.error("[SUBSCRIPTION-RENEWAL] Failed to process subscription {}: {}",
                subscription.getId(), e.getMessage(), e);
            subscription.setReconciliationStatus("ERROR");
            subscription.setReconciliationError(e.getMessage());
            subscription.setLastReconciliationAt(ZonedDateTime.now());
            // Return the subscription with error status so it can be logged
            return subscription;
        }
    }

    /**
     * Process subscription that has Stripe subscription ID by checking Stripe dates with fallback logic.
     */
    private MembershipSubscription processWithStripeCheck(MembershipSubscription subscription) {
        try {
            // Fetch subscription from Stripe
            Subscription stripeSubscription = stripeService.retrieveSubscription(
                subscription.getTenantId(),
                subscription.getStripeSubscriptionId()
            );

            // Get Stripe's current_period_end (Unix timestamp in seconds)
            Long stripePeriodEndUnix = stripeSubscription.getCurrentPeriodEnd();
            LocalDate stripePeriodEndDate = Instant.ofEpochSecond(stripePeriodEndUnix)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

            // Get Stripe's current_period_start
            Long stripePeriodStartUnix = stripeSubscription.getCurrentPeriodStart();
            LocalDate stripePeriodStartDate = Instant.ofEpochSecond(stripePeriodStartUnix)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

            // Calculate renewal window (X days from now)
            LocalDate renewalWindowEnd = LocalDate.now().plusDays(renewalDaysAhead);
            LocalDate today = LocalDate.now();

            // Convert to Date for comparison (for backward compatibility with shouldProcessSubscription)
            Date renewalWindowEndDate = Date.from(renewalWindowEnd.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date stripePeriodEndDateObj = Date.from(stripePeriodEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            // Log date comparison
            log.info("[SUBSCRIPTION-RENEWAL] Processing subscription ID: {}, Stripe ID: {}",
                subscription.getId(), subscription.getStripeSubscriptionId());
            log.info("[SUBSCRIPTION-RENEWAL] Database period_end: {}, Stripe period_end: {}, Renewal window end: {}",
                subscription.getCurrentPeriodEnd(), stripePeriodEndDate, renewalWindowEnd);

            // CRITICAL: Check if subscription should be processed (with fallback logic)
            if (!shouldProcessSubscription(subscription, stripeSubscription, renewalWindowEndDate, stripePeriodEndDateObj)) {
                return null; // Skip subscription
            }

            // Subscription should be processed - update database dates from Stripe (self-healing)
            boolean datesUpdated = false;
            if (!stripePeriodStartDate.equals(subscription.getCurrentPeriodStart())) {
                log.info("[SUBSCRIPTION-RENEWAL] Updating database period_start from {} to {}",
                    subscription.getCurrentPeriodStart(), stripePeriodStartDate);
                subscription.setCurrentPeriodStart(stripePeriodStartDate);
                datesUpdated = true;
            }

            if (!stripePeriodEndDate.equals(subscription.getCurrentPeriodEnd())) {
                log.info("[SUBSCRIPTION-RENEWAL] Updating database period_end from {} to {}",
                    subscription.getCurrentPeriodEnd(), stripePeriodEndDate);
                subscription.setCurrentPeriodEnd(stripePeriodEndDate);
                datesUpdated = true;
            }

            if (datesUpdated) {
                log.info("[SUBSCRIPTION-RENEWAL] Database dates updated from Stripe for subscription ID: {}",
                    subscription.getId());
                subscription.setReconciliationStatus("UPDATED");
            } else {
                subscription.setReconciliationStatus("SYNCED");
            }

            subscription.setLastReconciliationAt(ZonedDateTime.now());
            subscription.setLastStripeSyncAt(ZonedDateTime.now());

            log.info("[SUBSCRIPTION-RENEWAL] ✅ Subscription {} processed successfully. " +
                "Updated database dates from Stripe: period_start={}, period_end={}",
                subscription.getId(), subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());

            return subscription; // Process this subscription
        } catch (StripeException e) {
            log.error("[SUBSCRIPTION-RENEWAL] Error fetching Stripe subscription {} for tenant {}: {}",
                subscription.getStripeSubscriptionId(), subscription.getTenantId(), e.getMessage());

            // Fallback: use database date if Stripe fetch fails
            log.warn("[SUBSCRIPTION-RENEWAL] Falling back to database date check for subscription ID: {}",
                subscription.getId());
            return processWithoutStripeCheck(subscription);
        } catch (IllegalArgumentException e) {
            log.error("[SUBSCRIPTION-RENEWAL] Configuration error for subscription {} (tenant {}): {}",
                subscription.getStripeSubscriptionId(), subscription.getTenantId(), e.getMessage());

            // Fallback: use database date if Stripe configuration is missing
            log.warn("[SUBSCRIPTION-RENEWAL] Falling back to database date check for subscription ID: {}",
                subscription.getId());
            return processWithoutStripeCheck(subscription);
        }
    }

    /**
     * Determine if subscription needs renewal based on Stripe and database dates with fallback logic.
     *
     * @param subscription Database subscription entity
     * @param stripeSubscription Stripe subscription object
     * @param renewalWindowEnd End date of renewal window (CURRENT_DATE + X days)
     * @param stripePeriodEndDate Stripe period end date as Date object
     * @return true if subscription should be processed, false if skipped
     */
    private boolean shouldProcessSubscription(
        MembershipSubscription subscription,
        Subscription stripeSubscription,
        Date renewalWindowEnd,
        Date stripePeriodEndDate
    ) {
        // Get database period end date
        LocalDate databasePeriodEndLocal = subscription.getCurrentPeriodEnd();
        Date databasePeriodEndDate = databasePeriodEndLocal != null
            ? Date.from(databasePeriodEndLocal.atStartOfDay(ZoneId.systemDefault()).toInstant())
            : null;

        // Check if Stripe date is within renewal window
        boolean stripeWithinWindow = stripePeriodEndDate.before(renewalWindowEnd) ||
                                      stripePeriodEndDate.equals(renewalWindowEnd);

        if (stripeWithinWindow) {
            // Stripe date is within window - process subscription
            log.info("[SUBSCRIPTION-RENEWAL] Stripe period_end {} is within renewal window - processing",
                Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate());
            return true;
        }

        // Stripe date is outside window - check database date
        if (databasePeriodEndDate == null) {
            log.info("[SUBSCRIPTION-RENEWAL] Subscription {} skipped - database period_end is null and Stripe period_end {} is outside renewal window",
                subscription.getId(),
                Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate());
            return false;
        }

        boolean databaseWithinWindow = databasePeriodEndDate.before(renewalWindowEnd) ||
                                       databasePeriodEndDate.equals(renewalWindowEnd);

        if (!databaseWithinWindow) {
            // Both dates are outside window - skip subscription
            log.info("[SUBSCRIPTION-RENEWAL] Subscription {} skipped - both Stripe ({}) and database ({}) " +
                "period_end are outside renewal window (window end: {})",
                subscription.getId(),
                Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                databasePeriodEndLocal,
                Instant.ofEpochMilli(renewalWindowEnd.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate());
            return false;
        }

        // Database date is within window, but Stripe date is not - check fallback mode
        String fallbackProperty = environment.getProperty("subscription.renewal.use-database-fallback", "false");
        boolean useDatabaseFallback = Boolean.parseBoolean(fallbackProperty);
        log.debug("[SUBSCRIPTION-RENEWAL] Fallback mode check - property value: '{}', parsed as: {}",
            fallbackProperty, useDatabaseFallback);

        // Calculate days difference
        long daysDifference = ChronoUnit.DAYS.between(
            databasePeriodEndLocal,
            Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        );

        if (useDatabaseFallback) {
            // DEVELOPMENT/TESTING MODE: Use database date as fallback
            log.warn("[SUBSCRIPTION-RENEWAL] ⚠️ DATE MISMATCH DETECTED (Testing Mode): " +
                "Subscription {} - Database period_end: {}, Stripe period_end: {}, " +
                "Difference: {} days. Using database date for processing.",
                subscription.getId(), databasePeriodEndLocal,
                Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                daysDifference);

            // Update Stripe dates during processing (self-healing)
            log.info("[SUBSCRIPTION-RENEWAL] Will update database dates from Stripe during processing");
            return true; // Process subscription using database date
        } else {
            // PRODUCTION MODE: Always use Stripe dates (source of truth)
            log.warn("[SUBSCRIPTION-RENEWAL] ⚠️ DATE MISMATCH DETECTED (Production Mode): " +
                "Subscription {} - Database period_end: {}, Stripe period_end: {}, " +
                "Difference: {} days. Skipping subscription (Stripe is source of truth). " +
                "Consider running reconciliation job to sync dates.",
                subscription.getId(), databasePeriodEndLocal,
                Instant.ofEpochMilli(stripePeriodEndDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                daysDifference);

            return false; // Skip subscription - Stripe is source of truth
        }
    }

    /**
     * Process subscription without Stripe subscription ID using database dates only.
     */
    private MembershipSubscription processWithoutStripeCheck(MembershipSubscription subscription) {
        // Calculate renewal window (X days from now)
        LocalDate renewalWindowEnd = LocalDate.now().plusDays(renewalDaysAhead);

        // Check if database period_end is within renewal window
        if (subscription.getCurrentPeriodEnd() != null &&
            (subscription.getCurrentPeriodEnd().isBefore(renewalWindowEnd) ||
             subscription.getCurrentPeriodEnd().isEqual(renewalWindowEnd))) {

            subscription.setLastReconciliationAt(ZonedDateTime.now());
            subscription.setReconciliationStatus("PROCESSED");

            log.debug("[SUBSCRIPTION-RENEWAL] Processed subscription ID: {} using database dates",
                subscription.getId());
            return subscription;
        } else {
            log.debug("[SUBSCRIPTION-RENEWAL] Subscription {} skipped - database period_end {} is not within renewal window",
                subscription.getId(), subscription.getCurrentPeriodEnd());
            return null; // Skip this subscription
        }
    }
}
