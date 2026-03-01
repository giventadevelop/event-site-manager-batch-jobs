package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.DonationStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for DonationStatistics entity.
 */
@Repository
public interface DonationStatisticsRepository extends JpaRepository<DonationStatistics, Long> {

    /**
     * Find statistics by tenant, event, and date range.
     */
    Optional<DonationStatistics> findByTenantIdAndEventIdAndDateRangeStartAndDateRangeEnd(
        String tenantId,
        Long eventId,
        LocalDate dateRangeStart,
        LocalDate dateRangeEnd
    );

    /**
     * Find statistics by tenant and event (for current period).
     */
    @Query("SELECT s FROM DonationStatistics s " +
           "WHERE s.tenantId = :tenantId " +
           "AND (:eventId IS NULL OR s.eventId = :eventId) " +
           "ORDER BY s.lastUpdated DESC")
    java.util.List<DonationStatistics> findByTenantIdAndEventId(
        @Param("tenantId") String tenantId,
        @Param("eventId") Long eventId
    );
}
