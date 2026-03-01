package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PaymentProviderConfig;
import com.eventmanager.batch.repository.PaymentProviderConfigRepository;
import com.stripe.Stripe;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for processing Stripe refunds with retry logic and error handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeRefundService {

    private final PaymentProviderConfigRepository paymentProviderConfigRepository;
    private final EncryptionService encryptionService;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /**
     * Create a refund for a payment intent.
     *
     * @param tenantId The tenant ID
     * @param paymentIntentId The Stripe payment intent ID
     * @param ticketTransactionId The ticket transaction ID (for metadata)
     * @param jobId The batch job ID (for metadata)
     * @param eventId The event ID (for metadata)
     * @return Stripe Refund object
     * @throws StripeException if refund creation fails after retries
     */
    public Refund createRefund(
        String tenantId,
        String paymentIntentId,
        Long ticketTransactionId,
        String jobId,
        Long eventId
    ) throws StripeException {
        // Get Stripe API key for the tenant
        String apiKey = getStripeApiKey(tenantId);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Stripe API key not found for tenant: " + tenantId);
        }

        Stripe.apiKey = apiKey;

        RefundCreateParams params = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId)
            .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
            .putMetadata("ticket_transaction_id", ticketTransactionId.toString())
            .putMetadata("batch_job_id", jobId)
            .putMetadata("event_id", eventId.toString())
            .putMetadata("refund_reason", "Event canceled - Batch refund")
            .build();

        return createRefundWithRetry(params, paymentIntentId, tenantId);
    }

    /**
     * Create refund with retry logic for transient errors.
     */
    private Refund createRefundWithRetry(
        RefundCreateParams params,
        String paymentIntentId,
        String tenantId
    ) throws StripeException {
        StripeException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("Creating Stripe refund for payment intent {} (attempt {}/{})",
                    paymentIntentId, attempt, MAX_RETRIES);

                Refund refund = Refund.create(params);
                log.info("Successfully created Stripe refund {} for payment intent {}",
                    refund.getId(), paymentIntentId);
                return refund;

            } catch (RateLimitException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
                    log.warn("Rate limit exceeded for payment intent {} (attempt {}/{}). Retrying after {} ms",
                        paymentIntentId, attempt, MAX_RETRIES, delayMs);
                    delay(delayMs);
                } else {
                    log.error("Rate limit exceeded for payment intent {} after {} attempts",
                        paymentIntentId, MAX_RETRIES);
                }
            } catch (StripeException e) {
                // Check if it's a transient error (network, timeout)
                if (isTransientError(e) && attempt < MAX_RETRIES) {
                    lastException = e;
                    long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
                    log.warn("Transient error for payment intent {} (attempt {}/{}): {}. Retrying after {} ms",
                        paymentIntentId, attempt, MAX_RETRIES, e.getMessage(), delayMs);
                    delay(delayMs);
                } else {
                    // Permanent error or max retries reached
                    log.error("Failed to create refund for payment intent {}: {}",
                        paymentIntentId, e.getMessage());
                    throw e;
                }
            }
        }

        // All retries exhausted
        if (lastException != null) {
            throw lastException;
        }

        // If we get here, all retries were exhausted but no exception was stored (shouldn't happen)
        throw new RuntimeException("Failed to create refund after " + MAX_RETRIES + " attempts");
    }

    /**
     * Check if a Stripe exception is a transient error that should be retried.
     */
    private boolean isTransientError(StripeException e) {
        // Network errors, timeouts, and rate limits are transient
        String errorCode = e.getCode();
        Integer statusCode = e.getStatusCode();

        // Rate limit (429)
        if (statusCode != null && statusCode == 429) {
            return true;
        }

        // Network/timeout errors
        if (errorCode != null && (
            errorCode.contains("timeout") ||
            errorCode.contains("network") ||
            errorCode.contains("connection")
        )) {
            return true;
        }

        // 5xx server errors are typically transient
        if (statusCode != null && statusCode >= 500 && statusCode < 600) {
            return true;
        }

        return false;
    }

    /**
     * Get Stripe API secret key from PaymentProviderConfig for a tenant.
     */
    private String getStripeApiKey(String tenantId) {
        try {
            Optional<PaymentProviderConfig> configOpt = paymentProviderConfigRepository
                .findByTenantIdAndProvider(tenantId, "STRIPE");

            if (configOpt.isEmpty()) {
                log.warn("No Stripe configuration found for tenant: {}", tenantId);
                return null;
            }

            PaymentProviderConfig config = configOpt.get();

            // Try to get from encrypted field
            if (config.getProviderSecretKeyEncrypted() != null && !config.getProviderSecretKeyEncrypted().isEmpty()) {
                try {
                    String decryptedKey = encryptionService.decrypt(config.getProviderSecretKeyEncrypted());
                    log.debug("Successfully decrypted Stripe API key for tenant: {}", tenantId);
                    return decryptedKey;
                } catch (Exception e) {
                    log.error("Failed to decrypt Stripe API key for tenant {}: {}", tenantId, e.getMessage(), e);
                    return null;
                }
            }

            log.warn("Stripe secret key not found in configuration for tenant: {}", tenantId);
            return null;
        } catch (Exception e) {
            log.error("Error retrieving Stripe API key for tenant {}: {}", tenantId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Delay execution for retry backoff.
     */
    private void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted");
        }
    }
}
