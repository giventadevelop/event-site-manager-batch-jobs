package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for batch job execution.
 */
@Data
@Builder
public class BatchJobResponse {
    private Boolean success;
    private String message;
    private Long jobExecutionId;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
    private Long durationMs;
}















