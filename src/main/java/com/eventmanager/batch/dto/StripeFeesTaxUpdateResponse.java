package com.eventmanager.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * Response DTO for Stripe fees and tax update batch job.
 */
@Data
@Builder
public class StripeFeesTaxUpdateResponse {
    /**
     * Unique job ID for tracking.
     */
    private String jobId;

    /**
     * Job status: STARTED, IN_PROGRESS, COMPLETED, FAILED
     */
    private String status;

    /**
     * Tenant ID being processed (null if processing all tenants).
     */
    private String tenantId;

    /**
     * Start date filter (null if not provided).
     */
    private ZonedDateTime startDate;

    /**
     * End date filter (null if not provided).
     */
    private ZonedDateTime endDate;

    /**
     * Force update flag.
     */
    private Boolean forceUpdate;

    /**
     * Estimated number of records to process.
     */
    private Long estimatedRecords;

    /**
     * Estimated completion time.
     */
    private ZonedDateTime estimatedCompletionTime;

    /**
     * Human-readable message.
     */
    private String message;
}
