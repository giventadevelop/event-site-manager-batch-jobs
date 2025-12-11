package com.eventmanager.batch.job.email;

import com.eventmanager.batch.dto.EmailRecipient;
import com.eventmanager.batch.job.email.processor.EmailBatchProcessor;
import com.eventmanager.batch.job.email.reader.EmailBatchReader;
import com.eventmanager.batch.job.email.writer.EmailBatchWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Email Batch Job.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class EmailBatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EmailBatchReader emailBatchReader;
    private final EmailBatchProcessor emailBatchProcessor;
    private final EmailBatchWriter emailBatchWriter;

    @Value("${batch.email.batch-size:50}")
    private int batchSize;

    @Bean
    public Job emailBatchJob() {
        return new JobBuilder("emailBatchJob", jobRepository)
            .start(emailBatchStep())
            .build();
    }

    @Bean
    public Step emailBatchStep() {
        return new StepBuilder("emailBatchStep", jobRepository)
            .<EmailRecipient, EmailRecipient>chunk(batchSize, transactionManager)
            .reader(emailBatchReader)
            .processor(emailBatchProcessor)
            .writer(emailBatchWriter)
            .build();
    }
}

