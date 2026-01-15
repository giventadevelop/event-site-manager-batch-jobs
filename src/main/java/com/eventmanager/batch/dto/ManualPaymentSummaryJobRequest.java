package com.eventmanager.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for manual payment summary aggregation job.
 */
@Data
public class ManualPaymentSummaryJobRequest {
    private String tenantId;
    private Long eventId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate snapshotDate;
}
