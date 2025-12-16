package com.eventmanager.batch.dto;

import lombok.Data;
import java.util.List;

/**
 * Request DTO for batch job triggers.
 */
@Data
public class BatchJobRequest {
    private String tenantId;
    private Integer batchSize;
    private Integer maxSubscriptions;
    private Integer maxEmails;
    private Long templateId; // For email batch jobs
    private List<String> recipientEmails; // Optional: if not provided, will be fetched based on template
    private Long userId; // User ID who triggered the job (for logging)
    private String recipientType; // Optional: "EVENT_ATTENDEES" or "SUBSCRIBED_MEMBERS". If not provided, inferred from template.eventId
    private String stripeSubscriptionId; // Optional - if provided, process only this subscription
}




