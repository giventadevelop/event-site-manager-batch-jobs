package com.eventmanager.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for donation QR code job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DonationQrCodeJobResponse {

    private Boolean success;
    private String message;
    private Long jobExecutionId;
    private String qrCodeUrl;
    private String qrCodeImageUrl;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
}
