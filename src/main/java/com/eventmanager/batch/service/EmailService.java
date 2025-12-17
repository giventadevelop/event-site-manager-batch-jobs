package com.eventmanager.batch.service;

import com.google.common.util.concurrent.RateLimiter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending emails via AWS SES.
 * Handles rate limiting, circuit breaking, and batch email sending.
 */
@Service
@Slf4j
public class EmailService {

    private final SesClient sesClient;
    private final String fromAddress;
    private final RateLimiter sesRateLimiter;
    private final CircuitBreaker sesCircuitBreaker;

    public EmailService(
        @Value("${aws.s3.access-key}") String accessKey,
        @Value("${aws.s3.secret-key}") String secretKey,
        @Value("${aws.s3.region}") String region,
        @Value("${aws.ses.from-email}") String fromAddress,
        @Value("${aws.ses.rate-limit-per-second:200}") double sesRateLimitPerSecond
    ) {
        this.sesClient = SesClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
        this.fromAddress = fromAddress;

        this.sesRateLimiter = RateLimiter.create(sesRateLimitPerSecond);
        log.info("Initialized SES rate limiter with {} emails/second", sesRateLimitPerSecond);

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(100)
            .minimumNumberOfCalls(10)
            .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.sesCircuitBreaker = circuitBreakerRegistry.circuitBreaker("sesEmailSender");
        log.info("Initialized SES circuit breaker");
    }

    /**
     * Send a single email.
     */
    public void sendEmail(String to, String subject, String body, boolean isHtml) {
        if (sesCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("SES circuit breaker is OPEN, rejecting email send request");
            throw new RuntimeException("Email service is temporarily unavailable (circuit breaker open)");
        }

        if (!sesRateLimiter.tryAcquire()) {
            log.warn("SES rate limit exceeded, email send request rejected");
            throw new RuntimeException("Email rate limit exceeded, please try again later");
        }

        try {
            Body emailBody = isHtml
                ? Body.builder().html(Content.builder().data(body).build()).build()
                : Body.builder().text(Content.builder().data(body).build()).build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                    .subject(Content.builder().data(subject).build())
                    .body(emailBody)
                    .build())
                .source(fromAddress)
                .build();

            SendEmailResponse response = sesCircuitBreaker.executeSupplier(() ->
                sesClient.sendEmail(emailRequest));

            log.debug("Email sent successfully to {}: {}", to, response.messageId());
        } catch (SesException e) {
            log.error("Failed to send email via SES to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email via SES: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Send batch emails.
     */
    public void sendBatchEmails(String from, List<String> toAddresses, String subject, String body, boolean isHtml) {
        if (sesCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("SES circuit breaker is OPEN, rejecting batch email send request");
            throw new RuntimeException("Email service is temporarily unavailable (circuit breaker open)");
        }

        try {
            Body emailBody = isHtml
                ? Body.builder().html(Content.builder().data(body).build()).build()
                : Body.builder().text(Content.builder().data(body).build()).build();

            int batchSize = 50; // SES recommended batch size
            int totalBatches = (int) Math.ceil((double) toAddresses.size() / batchSize);
            int successfulBatches = 0;
            int failedBatches = 0;

            for (int i = 0; i < toAddresses.size(); i += batchSize) {
                if (!sesRateLimiter.tryAcquire()) {
                    log.warn("SES rate limit exceeded for batch {}/{}", (i / batchSize + 1), totalBatches);
                    failedBatches++;
                    continue;
                }

                int endIndex = Math.min(i + batchSize, toAddresses.size());
                List<String> batch = toAddresses.subList(i, endIndex);

                try {
                    SendEmailRequest emailRequest = SendEmailRequest.builder()
                        .destination(Destination.builder().toAddresses(batch).build())
                        .message(Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(emailBody)
                            .build())
                        .source(from)
                        .build();

                    SendEmailResponse response = sesCircuitBreaker.executeSupplier(() ->
                        sesClient.sendEmail(emailRequest));

                    successfulBatches++;
                    log.debug("Sent batch {}/{} from {} to {} recipients: {}",
                        (i / batchSize + 1), totalBatches, from, batch.size(), response.messageId());
                } catch (Exception e) {
                    failedBatches++;
                    log.error("Failed to send batch {}/{} via SES: {}", (i / batchSize + 1), totalBatches, e.getMessage(), e);
                }
            }

            log.info("Batch email sending completed from {}: {} successful batches, {} failed batches, {} total recipients",
                from, successfulBatches, failedBatches, toAddresses.size());
        } catch (Exception e) {
            log.error("Failed to send batch emails via SES from {}: {}", from, e.getMessage(), e);
            throw new RuntimeException("Failed to send batch emails via SES: " + e.getMessage(), e);
        }
    }
}









