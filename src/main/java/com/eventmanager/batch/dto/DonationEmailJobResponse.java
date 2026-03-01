package com.eventmanager.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for donation email job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DonationEmailJobResponse {

    private Boolean success;
    private String message;
    private Long jobExecutionId;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
}
