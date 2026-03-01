package com.eventmanager.batch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for donation QR code job.
 * Triggered by API endpoint to generate QR codes for donations.
 */
@Data
public class DonationQrCodeJobRequest {

    @NotNull
    private Long donationId;

    @NotNull
    private Long eventId;

    @NotNull
    private String tenantId;
}
