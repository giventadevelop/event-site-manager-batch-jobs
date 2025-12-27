package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.MembershipSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, Long> {

    @Query("SELECT s FROM MembershipSubscription s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.subscriptionStatus IN ('ACTIVE', 'TRIALING') " +
           "AND s.cancelAtPeriodEnd = false " +
           "AND (" +
           "  s.currentPeriodEnd <= :renewalDateThreshold " + // Database date check
           "  OR (s.stripeSubscriptionId IS NOT NULL " + // Will check Stripe dates in processor
           "      AND s.currentPeriodEnd <= :extendedDateThreshold)" + // Extended window for Stripe-checked subscriptions
           ") " +
           "ORDER BY s.currentPeriodEnd ASC")
    List<MembershipSubscription> findSubscriptionsNeedingRenewal(
        @Param("tenantId") String tenantId,
        @Param("renewalDateThreshold") LocalDate renewalDateThreshold,
        @Param("extendedDateThreshold") LocalDate extendedDateThreshold
    );

    @Query("SELECT s FROM MembershipSubscription s " +
           "WHERE s.stripeSubscriptionId = :stripeSubscriptionId " +
           "AND s.tenantId = :tenantId")
    List<MembershipSubscription> findByStripeSubscriptionIdAndTenantId(
        @Param("stripeSubscriptionId") String stripeSubscriptionId,
        @Param("tenantId") String tenantId
    );
}
