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
    private final EncryptionService encryptionService;

    // Cache to track last initialized tenant to avoid repeated DB reads and decryption
    private String lastInitializedTenantId;
    private String cachedApiKey;

    /**
     * Initialize Stripe API key for a tenant.
     * Caches the API key per tenant to avoid repeated DB reads and decryption.
     * Only reads from DB and decrypts if tenant changed.
     */
    public void initStripe(String tenantId) {
        // If already initialized for this tenant, skip DB read and decryption
        if (lastInitializedTenantId != null && lastInitializedTenantId.equals(tenantId) && cachedApiKey != null) {
            log.debug("Stripe already initialized for tenant: {}, reusing cached API key", tenantId);
            Stripe.apiKey = cachedApiKey;
            return;
        }

        log.debug("Initializing Stripe for tenant: {} (reading from DB and decrypting)", tenantId);

        Optional<PaymentProviderConfig> configOpt = paymentProviderConfigRepository
            .findByTenantIdAndProvider(tenantId, "STRIPE");

        if (configOpt.isEmpty()) {
            log.error("Stripe configuration not found in database for tenant: {}", tenantId);
            throw new RuntimeException("Stripe configuration not found for tenant: " + tenantId);
        }

        try {
            PaymentProviderConfig config = configOpt.get();
            log.debug("Found PaymentProviderConfig - ID: {}, TenantId: {}, Provider: {}",
                config.getId(), config.getTenantId(), config.getProvider());

            String apiKey = null;

            // Priority 1: Try to get from encrypted field (preferred method)
            String encryptedSecretKey = config.getProviderSecretKeyEncrypted();
            log.debug("Retrieved providerSecretKeyEncrypted: {}",
                encryptedSecretKey != null ? (encryptedSecretKey.length() > 50 ?
                    encryptedSecretKey.substring(0, 50) + "..." : encryptedSecretKey) : "NULL");

            if (encryptedSecretKey != null && !encryptedSecretKey.isEmpty()) {
                try {
                    log.info("Attempting to decrypt provider_secret_key_encrypted for tenant: {} (length: {})",
                        tenantId, encryptedSecretKey.length());
                    apiKey = encryptionService.decrypt(encryptedSecretKey);
                    log.info("Successfully decrypted Stripe secret key from provider_secret_key_encrypted");
                } catch (Exception e) {
                    log.warn("Failed to decrypt provider_secret_key_encrypted for tenant {}: {}. " +
                            "Falling back to configuration_json.", tenantId, e.getMessage(), e);
                }
            }

            // Priority 2: Fallback to configuration_json (legacy method)
            if (apiKey == null || apiKey.isEmpty()) {
                String configJson = config.getConfigJson();
                log.debug("Retrieved configJson value: {}", configJson != null ?
                    (configJson.length() > 100 ? configJson.substring(0, 100) + "..." : configJson) : "NULL");

                if (configJson != null && !configJson.isEmpty()) {
                    try {
                        Map<String, Object> configMap = objectMapper.readValue(configJson, Map.class);
                        apiKey = (String) configMap.get("secretKey");
                        if (apiKey != null && !apiKey.isEmpty()) {
                            log.debug("Successfully retrieved Stripe secret key from configuration_json");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse configuration_json for tenant {}: {}", tenantId, e.getMessage());
                    }
                }
            }

            // Validate that we have an API key
            if (apiKey == null || apiKey.isEmpty()) {
                String errorMessage = String.format(
                    "CRITICAL: Stripe secret key not found for tenant: %s. " +
                    "PaymentProviderConfig ID: %d, Provider: %s. " +
                    "The batch job requires Stripe API keys to sync subscription data from Stripe. " +
                    "Please ensure either provider_secret_key_encrypted or configuration_json contains the Stripe secret key.",
                    tenantId, config.getId(), config.getProvider());
                log.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }

            // Cache the API key and tenant ID
            Stripe.apiKey = apiKey;
            cachedApiKey = apiKey;
            lastInitializedTenantId = tenantId;
            log.debug("Initialized and cached Stripe API key for tenant: {}", tenantId);
        } catch (RuntimeException e) {
            // Re-throw RuntimeException as-is
            log.error("Failed to initialize Stripe for tenant {}: {}", tenantId, e.getMessage(), e);
            throw e;
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








