package com.eventmanager.batch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO representing a single contact form email job.
 */
@Data
public class ContactFormEmailJobRequest {

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    @NotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank
    @Size(max = 255)
    private String lastName;

    @NotBlank
    @Size(max = 4096)
    private String messageBody;

    @NotBlank
    @Email
    @Size(max = 255)
    private String fromEmail;

    @NotBlank
    @Email
    @Size(max = 255)
    private String toEmail;

    // Optional metadata for logging/auditing
    private Long submittedAtEpochMillis;

    private Long userId;
}






