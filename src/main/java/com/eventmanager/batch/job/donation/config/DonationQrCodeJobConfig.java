package com.eventmanager.batch.job.donation.config;

import com.eventmanager.batch.domain.DonationTransaction;
import com.eventmanager.batch.job.donation.processor.DonationQrCodeItemProcessor;
import com.eventmanager.batch.job.donation.reader.DonationQrCodeItemReader;
import com.eventmanager.batch.job.donation.writer.DonationQrCodeItemWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Donation QR Code Batch Job.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DonationQrCodeJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DonationQrCodeItemReader donationQrCodeReader;
    private final DonationQrCodeItemProcessor donationQrCodeProcessor;
    private final DonationQrCodeItemWriter donationQrCodeWriter;

    @Value("${batch.donation.qrcode.batch-size:10}")
    private int batchSize;

    @Bean
    public Job donationQrCodeJob() {
        return new JobBuilder("donationQrCodeJob", jobRepository)
            .start(donationQrCodeStep())
            .build();
    }

    @Bean
    public Step donationQrCodeStep() {
        return new StepBuilder("donationQrCodeStep", jobRepository)
            .<DonationTransaction, DonationTransaction>chunk(batchSize, transactionManager)
            .reader(donationQrCodeReader)
            .processor(donationQrCodeProcessor)
            .writer(donationQrCodeWriter)
            .faultTolerant()
            .retryLimit(3)
            .retry(RuntimeException.class)
            .skipLimit(10)
            .skip(Exception.class)
            .build();
    }
}
