package com.eventmanager.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * Request DTO for Stripe fees and tax update batch job.
 */
@Data
public class StripeFeesTaxUpdateRequest {
    /**
     * Optional tenant ID to filter by. If not provided, processes all tenants.
     */
    private String tenantId;

    /**
     * Optional event ID to filter by. If not provided, processes all events.
     */
    private Long eventId;

    /**
     * Optional start date (ISO 8601 format). Process transactions with purchase date on or after this date.
     * If useDefaultDateRange is true, this will be calculated automatically (first day of current month).
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime startDate;

    /**
     * Optional end date (ISO 8601 format). Process transactions with purchase date on or before this date.
     * If useDefaultDateRange is true, this will be calculated automatically (today minus 14 days).
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime endDate;

    /**
     * If true, update even if stripe_fee_amount is already populated. Default: false.
     */
    private Boolean forceUpdate = false;

    /**
     * If true, automatically calculate date range for normal batch runs:
     * - Start Date: First day of current month
     * - End Date: Today minus 14 days (to ensure Stripe payout is complete)
     * Default: false (use provided dates or process all if not provided)
     */
    private Boolean useDefaultDateRange = false;
}
