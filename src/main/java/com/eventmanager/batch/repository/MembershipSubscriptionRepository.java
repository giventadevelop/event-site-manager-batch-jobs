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
           "AND s.currentPeriodEnd <= :renewalDateThreshold " +
           "AND s.cancelAtPeriodEnd = false " +
           "ORDER BY s.currentPeriodEnd ASC")
    List<MembershipSubscription> findSubscriptionsNeedingRenewal(
        @Param("tenantId") String tenantId,
        @Param("renewalDateThreshold") LocalDate renewalDateThreshold
    );

    @Query("SELECT s FROM MembershipSubscription s " +
           "WHERE s.stripeSubscriptionId = :stripeSubscriptionId " +
           "AND s.tenantId = :tenantId")
    List<MembershipSubscription> findByStripeSubscriptionIdAndTenantId(
        @Param("stripeSubscriptionId") String stripeSubscriptionId,
        @Param("tenantId") String tenantId
    );
}
