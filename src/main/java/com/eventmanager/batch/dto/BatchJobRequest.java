package com.eventmanager.batch.dto;

import lombok.Data;

/**
 * Request DTO for batch job triggers.
 */
@Data
public class BatchJobRequest {
    private String tenantId;
    private Integer batchSize;
    private Integer maxSubscriptions;
    private Integer maxEmails;
}


