package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Response DTO for manual payment summary aggregation job.
 */
@Data
@Builder
public class ManualPaymentSummaryJobResponse {
    private Boolean success;
    private String message;
    private Long jobExecutionId;
    private String tenantId;
    private Long eventId;
    private LocalDate snapshotDate;
    private Integer deletedRows;
    private Integer insertedRows;
}
