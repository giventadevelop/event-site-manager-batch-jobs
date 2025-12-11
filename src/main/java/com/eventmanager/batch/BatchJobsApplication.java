package com.eventmanager.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Batch Jobs Service.
 * This service handles subscription renewal batch jobs and email batch processing.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.eventmanager.batch.repository")
@EntityScan(basePackages = "com.eventmanager.batch.domain")
public class BatchJobsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchJobsApplication.class, args);
    }
}




