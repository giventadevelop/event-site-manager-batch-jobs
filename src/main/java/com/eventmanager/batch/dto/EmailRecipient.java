package com.eventmanager.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for email recipient in batch job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRecipient {
    private String email;
    private Long templateId;
    private String tenantId;
    private Long eventId;
    private String subject;
    private String bodyHtml;
    private String fromEmail;
    private String promotionCode;
    private Long discountCodeId;
    private Long sentById;
}

