package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.BatchJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for BatchJobExecution entity.
 */
@Repository
public interface BatchJobExecutionRepository extends JpaRepository<BatchJobExecution, Long> {

    /**
     * Find recent job executions by job name.
     */
    List<BatchJobExecution> findByJobNameOrderByStartedAtDesc(String jobName);

    /**
     * Find job executions by status.
     */
    List<BatchJobExecution> findByStatusOrderByStartedAtDesc(String status);
}











