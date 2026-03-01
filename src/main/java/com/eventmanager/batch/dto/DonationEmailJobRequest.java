package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for donation email job.
 * Triggered by API endpoint to send donation confirmation emails.
 */
@Data
public class DonationEmailJobRequest {

    @NotNull
    private Long donationId;

    @NotNull
    private Long eventId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String recipientEmail;

    @NotBlank
    @Size(max = 255)
    private String tenantId;
}
