package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PaymentProviderConfig;
import com.eventmanager.batch.repository.PaymentProviderConfigRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for interacting with Stripe API.
 * Handles subscription retrieval and sync operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final PaymentProviderConfigRepository paymentProviderConfigRepository;
    private final EncryptionService encryptionService;

    /**
     * Retrieve a subscription from Stripe.
     *
     * @param tenantId The tenant ID
     * @param stripeSubscriptionId The Stripe subscription ID
     * @return Stripe Subscription object
     * @throws StripeException if there's an error fetching from Stripe
     * @throws IllegalArgumentException if Stripe API key is not found for the tenant
     */
    public Subscription retrieveSubscription(String tenantId, String stripeSubscriptionId) throws StripeException {
        // Get Stripe API key for the tenant
        String apiKey = getStripeApiKey(tenantId);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Stripe API key not found for tenant: " + tenantId);
        }

        // Set the API key for this request
        Stripe.apiKey = apiKey;

        log.debug("Retrieving Stripe subscription {} for tenant {}", stripeSubscriptionId, tenantId);

        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            log.debug("Successfully retrieved Stripe subscription {} for tenant {}", stripeSubscriptionId, tenantId);
            return subscription;
        } catch (StripeException e) {
            log.error("Error retrieving Stripe subscription {} for tenant {}: {}",
                stripeSubscriptionId, tenantId, e.getMessage());
            throw e;
        }
    }

    /**
     * Get Stripe API secret key from PaymentProviderConfig for a tenant.
     * Decrypts the encrypted secret key using the encryption service.
     *
     * @param tenantId The tenant ID
     * @return Stripe API secret key (decrypted), or null if not found
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

            // First try to get from encrypted field
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
}
