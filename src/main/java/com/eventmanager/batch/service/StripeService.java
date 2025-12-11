package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PaymentProviderConfig;
import com.eventmanager.batch.repository.PaymentProviderConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for interacting with Stripe API.
 * Handles tenant-specific Stripe API key management and subscription retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final PaymentProviderConfigRepository paymentProviderConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * Initialize Stripe API key for a tenant.
     */
    public void initStripe(String tenantId) {
        Optional<PaymentProviderConfig> configOpt = paymentProviderConfigRepository
            .findByTenantIdAndProvider(tenantId, "STRIPE");

        if (configOpt.isEmpty()) {
            throw new RuntimeException("Stripe configuration not found for tenant: " + tenantId);
        }

        try {
            PaymentProviderConfig config = configOpt.get();
            Map<String, Object> configMap = objectMapper.readValue(config.getConfigJson(), Map.class);
            String apiKey = (String) configMap.get("secretKey");

            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("Stripe secret key not configured for tenant: " + tenantId);
            }

            Stripe.apiKey = apiKey;
            log.debug("Initialized Stripe API key for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to initialize Stripe for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Stripe: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a Stripe subscription.
     */
    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    /**
     * Map Stripe subscription status to local status.
     */
    public String mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null || stripeStatus.isEmpty()) {
            return "ACTIVE";
        }

        return switch (stripeStatus.toLowerCase()) {
            case "active" -> "ACTIVE";
            case "trialing" -> "TRIAL";
            case "past_due" -> "PAST_DUE";
            case "canceled", "cancelled" -> "CANCELLED";
            case "unpaid" -> "SUSPENDED";
            case "incomplete", "incomplete_expired" -> "EXPIRED";
            default -> "ACTIVE";
        };
    }
}




