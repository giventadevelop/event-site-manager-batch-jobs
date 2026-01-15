package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO representing a promotion test email job.
 * Sends one promotion email template to exactly one recipient.
 *
 * Mirrors the ContactFormEmailJobRequest pattern (tenant-scoped, async).
 */
@Data
public class PromotionTestEmailJobRequest {

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    @NotNull
    private Long templateId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String recipientEmail;

    /**
     * Optional metadata for auditing/logging.
     */
    private Long submittedAtEpochMillis;

    /**
     * Optional user who initiated the send.
     */
    private Long userId;
}

