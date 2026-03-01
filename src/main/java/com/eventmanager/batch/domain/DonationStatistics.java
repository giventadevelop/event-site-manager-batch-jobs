package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Entity representing pre-aggregated donation statistics.
 * This mirrors the donation_statistics table from the main backend database.
 */
@Entity
@Table(name = "donation_statistics")
@Data
public class DonationStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "total_donations", nullable = false)
    private Integer totalDonations = 0;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "average_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal averageAmount = BigDecimal.ZERO;

    @Column(name = "date_range_start")
    private LocalDate dateRangeStart;

    @Column(name = "date_range_end")
    private LocalDate dateRangeEnd;

    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;
}
