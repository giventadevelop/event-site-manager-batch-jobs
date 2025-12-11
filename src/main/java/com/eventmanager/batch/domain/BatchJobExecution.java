package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Entity for tracking batch job executions.
 * Stores metadata about batch job runs for monitoring and auditing.
 * This is a custom application table, separate from Spring Batch's BATCH_JOB_EXECUTION table.
 */
@Entity
@Table(name = "batch_job_execution_log")
@Data
public class BatchJobExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType; // SUBSCRIPTION_RENEWAL, EMAIL_BATCH, etc.

    @Column(name = "status", nullable = false, length = 20)
    private String status; // RUNNING, COMPLETED, FAILED, CANCELLED

    @Column(name = "tenant_id", length = 255)
    private String tenantId; // null for multi-tenant jobs

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "processed_count")
    private Long processedCount = 0L;

    @Column(name = "success_count")
    private Long successCount = 0L;

    @Column(name = "failed_count")
    private Long failedCount = 0L;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy; // SCHEDULED, API, MANUAL

    @Column(name = "parameters_json", columnDefinition = "text")
    private String parametersJson; // JSON string with job parameters (stored as TEXT)
}

