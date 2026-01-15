package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for promotion test email job execution.
 *
 * Mirrors ContactFormEmailJobResponse.
 */
@Data
@Builder
public class PromotionTestEmailJobResponse {

    private Boolean success;

    private String message;

    private Long jobExecutionId;

    private Long processedCount;

    private Long successCount;

    private Long failedCount;
}

