package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.MembershipSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for MembershipSubscription entity.
 */
@Repository
public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, Long> {

    /**
     * Find subscriptions by Stripe subscription ID.
     */
    MembershipSubscription findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Find subscriptions approaching renewal date.
     * Returns subscriptions that need renewal check within the specified days.
     */
    @Query("SELECT s FROM MembershipSubscription s WHERE " +
           "s.subscriptionStatus IN ('ACTIVE', 'TRIAL') " +
           "AND s.currentPeriodEnd <= :checkDate " +
           "AND s.cancelAtPeriodEnd = false " +
           "AND s.stripeSubscriptionId IS NOT NULL " +
           "ORDER BY s.currentPeriodEnd ASC")
    List<MembershipSubscription> findSubscriptionsApproachingRenewal(@Param("checkDate") LocalDate checkDate);

    /**
     * Find subscriptions by tenant and status.
     */
    List<MembershipSubscription> findByTenantIdAndSubscriptionStatus(String tenantId, String status);

    /**
     * Find subscriptions by tenant that need reconciliation.
     */
    @Query("SELECT s FROM MembershipSubscription s WHERE " +
           "s.tenantId = :tenantId " +
           "AND s.subscriptionStatus IN ('ACTIVE', 'TRIAL', 'PAST_DUE') " +
           "AND s.stripeSubscriptionId IS NOT NULL " +
           "ORDER BY s.id ASC")
    List<MembershipSubscription> findActiveSubscriptionsForTenant(@Param("tenantId") String tenantId);
}


