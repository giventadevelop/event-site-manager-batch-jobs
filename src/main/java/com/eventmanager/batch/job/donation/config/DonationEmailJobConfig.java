package com.eventmanager.batch.job.donation.config;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.job.donation.processor.DonationEmailItemProcessor;
import com.eventmanager.batch.job.donation.reader.DonationEmailItemReader;
import com.eventmanager.batch.job.donation.writer.DonationEmailItemWriter;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Donation Email Batch Job.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DonationEmailJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DonationEmailItemReader donationEmailReader;
    private final DonationEmailItemProcessor donationEmailProcessor;
    private final DonationEmailItemWriter donationEmailWriter;

    @Value("${batch.donation.email.batch-size:10}")
    private int batchSize;

    @Bean
    public Job donationEmailJob() {
        return new JobBuilder("donationEmailJob", jobRepository)
            .start(donationEmailStep())
            .build();
    }

    @Bean
    public Step donationEmailStep() {
        return new StepBuilder("donationEmailStep", jobRepository)
            .<DonationTransaction, DonationTransaction>chunk(batchSize, transactionManager)
            .reader(donationEmailReader)
            .processor(donationEmailProcessor)
            .writer(donationEmailWriter)
            .faultTolerant()
            .retryLimit(3)
            .retry(RuntimeException.class)
            .skipLimit(10)
            .skip(Exception.class)
            .build();
    }
}
