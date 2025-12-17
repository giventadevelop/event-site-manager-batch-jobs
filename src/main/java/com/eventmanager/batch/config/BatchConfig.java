package com.eventmanager.batch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for Spring Batch.
 */
@Configuration
@EnableBatchProcessing
@EnableScheduling
public class BatchConfig {

    /**
     * Task executor for async batch job processing.
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(5); // Limit concurrent batch jobs
        executor.setThreadNamePrefix("batch-job-");
        return executor;
    }
}









