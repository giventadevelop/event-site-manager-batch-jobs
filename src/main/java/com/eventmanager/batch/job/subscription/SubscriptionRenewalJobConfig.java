package com.eventmanager.batch.job.subscription;

import com.eventmanager.batch.domain.MembershipSubscription;
import com.eventmanager.batch.job.subscription.processor.SubscriptionRenewalProcessor;
import com.eventmanager.batch.job.subscription.reader.SubscriptionRenewalReader;
import com.eventmanager.batch.job.subscription.writer.SubscriptionRenewalWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Subscription Renewal Batch Job.
 * Processes subscriptions approaching renewal date and syncs with Stripe.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SubscriptionRenewalReader reader;
    private final SubscriptionRenewalProcessor processor;
    private final SubscriptionRenewalWriter writer;

    @Value("${batch.subscription-renewal.batch-size:100}")
    private int batchSize;

    /**
     * Subscription Renewal Job.
     */
    @Bean
    public Job subscriptionRenewalJob() {
        return new JobBuilder("subscriptionRenewalJob", jobRepository)
            .start(subscriptionRenewalStep())
            .build();
    }

    /**
     * Subscription Renewal Step.
     */
    @Bean
    public Step subscriptionRenewalStep() {
        return new StepBuilder("subscriptionRenewalStep", jobRepository)
            .<MembershipSubscription, MembershipSubscription>chunk(batchSize, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}

















