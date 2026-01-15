package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import com.eventmanager.batch.repository.BatchJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Service for managing batch job execution records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobExecutionService {

    private final BatchJobExecutionRepository batchJobExecutionRepository;
    private final SequenceSynchronizationService sequenceSynchronizationService;

    /**
     * Create a new batch job execution record.
     */
    @Transactional
    public BatchJobExecution createJobExecution(String jobName, String jobType, String tenantId, String triggeredBy, String parametersJson) {
        try {
            log.debug("Pre-synchronizing sequence_generator before batch job execution: {}", jobName);
            sequenceSynchronizationService.synchronizeSequence();
        } catch (Exception e) {
            log.warn("Failed to pre-synchronize sequence before batch job execution. " +
                "Will rely on AOP aspect for recovery: {}", e.getMessage());
        }

        BatchJobExecution execution = new BatchJobExecution();
        execution.setJobName(jobName);
        execution.setJobType(jobType);
        execution.setTenantId(tenantId);
        execution.setStatus("RUNNING");
        execution.setStartedAt(ZonedDateTime.now());
        execution.setTriggeredBy(triggeredBy);
        execution.setParametersJson(parametersJson);
        execution.setProcessedCount(0L);
        execution.setSuccessCount(0L);
        execution.setFailedCount(0L);

        return batchJobExecutionRepository.save(execution);
    }

    /**
     * Update job execution with completion status.
     */
    @Transactional
    public void completeJobExecution(Long executionId, String status, Long processedCount, Long successCount, Long failedCount, String errorMessage) {
        BatchJobExecution execution = batchJobExecutionRepository.findById(executionId)
            .orElseThrow(() -> new RuntimeException("Job execution not found: " + executionId));

        execution.setStatus(status);
        execution.setCompletedAt(ZonedDateTime.now());
        execution.setProcessedCount(processedCount);
        execution.setSuccessCount(successCount);
        execution.setFailedCount(failedCount);
        execution.setErrorMessage(errorMessage);

        if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
            long durationMs = java.time.Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis();
            execution.setDurationMs(durationMs);
        }

        batchJobExecutionRepository.save(execution);
    }

    /**
     * Get recent job executions.
     */
    public List<BatchJobExecution> getRecentExecutions(String jobName) {
        return batchJobExecutionRepository.findByJobNameOrderByStartedAtDesc(jobName);
    }
}






















