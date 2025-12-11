package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Entity representing a membership subscription.
 * This mirrors the membership_subscription table from the main backend database.
 */
@Entity
@Table(name = "membership_subscription")
@Data
public class MembershipSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "user_profile_id", nullable = false)
    private Long userProfileId;

    @Column(name = "membership_plan_id", nullable = false)
    private Long membershipPlanId;

    @Column(name = "subscription_status", nullable = false, length = 20)
    private String subscriptionStatus = "ACTIVE";

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "trial_start")
    private LocalDate trialStart;

    @Column(name = "trial_end")
    private LocalDate trialEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "payment_provider_config_id")
    private Long paymentProviderConfigId;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "last_reconciliation_at")
    private ZonedDateTime lastReconciliationAt;

    @Column(name = "last_stripe_sync_at")
    private ZonedDateTime lastStripeSyncAt;

    @Column(name = "reconciliation_status", length = 20)
    private String reconciliationStatus = "PENDING";

    @Column(name = "reconciliation_error", columnDefinition = "text")
    private String reconciliationError;
}




