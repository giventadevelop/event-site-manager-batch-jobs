package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PaymentProviderConfig;
import com.eventmanager.batch.repository.PaymentProviderConfigRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for retrieving Stripe fee and tax data from Stripe API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeFeesTaxService {

    private final PaymentProviderConfigRepository paymentProviderConfigRepository;
    private final EncryptionService encryptionService;

    // Cache for decrypted Stripe API keys per tenant (read once per batch job run)
    private final Map<String, String> stripeApiKeyCache = new ConcurrentHashMap<>();

    /**
     * Retrieve Stripe fee amount for a payment intent.
     *
     * @param tenantId The tenant ID
     * @param paymentIntentId The Stripe payment intent ID
     * @return Stripe fee amount in dollars, or null if not found
     */
    public BigDecimal getStripeFee(String tenantId, String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.warn("Payment intent ID is null or empty for tenant: {}", tenantId);
            return null;
        }

        String apiKey = getStripeApiKey(tenantId);
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Stripe API key not found for tenant: {}", tenantId);
            return null;
        }

        try {
            // Set the API key for this request
            Stripe.apiKey = apiKey;

            // Step 1: Retrieve PaymentIntent
            log.debug("Retrieving PaymentIntent {} for tenant {}", paymentIntentId, tenantId);
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            // Step 2: Get charges for this PaymentIntent
            Map<String, Object> chargeParams = new java.util.HashMap<>();
            chargeParams.put("payment_intent", paymentIntentId);
            chargeParams.put("limit", 1);

            List<Charge> charges = Charge.list(chargeParams).getData();

            if (charges.isEmpty()) {
                log.warn("No charges found for PaymentIntent {} for tenant {}", paymentIntentId, tenantId);
                return null;
            }

            Charge charge = charges.get(0);

            // Step 3: Get balance transaction (contains fee)
            if (charge.getBalanceTransaction() == null) {
                log.warn("Charge {} missing balance_transaction for tenant {}", charge.getId(), tenantId);
                return null;
            }

            String balanceTransactionId = charge.getBalanceTransaction();
            log.debug("Retrieving balance transaction {} for tenant {}", balanceTransactionId, tenantId);
            BalanceTransaction balanceTx = BalanceTransaction.retrieve(balanceTransactionId);

            // Step 4: Extract fee (convert from cents to dollars)
            Long feeInCents = balanceTx.getFee();
            if (feeInCents == null) {
                log.warn("Balance transaction {} has null fee for tenant {}", balanceTransactionId, tenantId);
                return null;
            }

            BigDecimal feeAmount = BigDecimal.valueOf(feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            log.debug("Retrieved Stripe fee {} for PaymentIntent {} for tenant {}", feeAmount, paymentIntentId, tenantId);
            return feeAmount;

        } catch (StripeException e) {
            log.error("Stripe API error retrieving fee for PaymentIntent {} for tenant {}: {}",
                paymentIntentId, tenantId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error retrieving Stripe fee for PaymentIntent {} for tenant {}: {}",
                paymentIntentId, tenantId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieve Stripe fee and net payout amount for a payment intent.
     * The net amount is the actual amount that will be deposited to the bank account after Stripe fees.
     *
     * @param tenantId The tenant ID
     * @param paymentIntentId The Stripe payment intent ID
     * @return StripeFeeNetResult containing fee and net amounts, or null if not found
     */
    public StripeFeeNetResult getStripeFeeAndNet(String tenantId, String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.warn("Payment intent ID is null or empty for tenant: {}", tenantId);
            return null;
        }

        String apiKey = getStripeApiKey(tenantId);
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Stripe API key not found for tenant: {}", tenantId);
            return null;
        }

        try {
            // Set the API key for this request
            Stripe.apiKey = apiKey;

            // Step 1: Retrieve PaymentIntent
            log.debug("Retrieving PaymentIntent {} for tenant {}", paymentIntentId, tenantId);
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            // Step 2: Get charges for this PaymentIntent
            Map<String, Object> chargeParams = new java.util.HashMap<>();
            chargeParams.put("payment_intent", paymentIntentId);
            chargeParams.put("limit", 1);

            List<Charge> charges = Charge.list(chargeParams).getData();

            if (charges.isEmpty()) {
                log.warn("No charges found for PaymentIntent {} for tenant {}", paymentIntentId, tenantId);
                return null;
            }

            Charge charge = charges.get(0);

            // Step 3: Get balance transaction (contains fee and net)
            if (charge.getBalanceTransaction() == null) {
                log.warn("Charge {} missing balance_transaction for tenant {}", charge.getId(), tenantId);
                return null;
            }

            String balanceTransactionId = charge.getBalanceTransaction();
            log.debug("Retrieving balance transaction {} for tenant {}", balanceTransactionId, tenantId);
            BalanceTransaction balanceTx = BalanceTransaction.retrieve(balanceTransactionId);

            // Step 4: Extract fee (convert from cents to dollars)
            Long feeInCents = balanceTx.getFee();
            if (feeInCents == null) {
                log.warn("Balance transaction {} has null fee for tenant {}", balanceTransactionId, tenantId);
                return null;
            }
            BigDecimal feeAmount = BigDecimal.valueOf(feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Step 5: Extract net amount (convert from cents to dollars)
            Long netInCents = balanceTx.getNet();
            BigDecimal netAmount = null;
            if (netInCents != null) {
                netAmount = BigDecimal.valueOf(netInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                log.debug("Retrieved Stripe net amount {} for PaymentIntent {} for tenant {}", netAmount, paymentIntentId, tenantId);
            } else {
                log.warn("Balance transaction {} has null net amount for tenant {}", balanceTransactionId, tenantId);
            }

            log.debug("Retrieved Stripe fee {} and net {} for PaymentIntent {} for tenant {}",
                feeAmount, netAmount, paymentIntentId, tenantId);
            return new StripeFeeNetResult(feeAmount, netAmount);

        } catch (StripeException e) {
            log.error("Stripe API error retrieving fee and net for PaymentIntent {} for tenant {}: {}",
                paymentIntentId, tenantId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error retrieving Stripe fee and net for PaymentIntent {} for tenant {}: {}",
                paymentIntentId, tenantId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Result class for Stripe fee and net amount retrieval.
     */
    @Data
    @AllArgsConstructor
    public static class StripeFeeNetResult {
        private BigDecimal fee;
        private BigDecimal net;
    }

    /**
     * Retrieve Stripe tax amount for a payment intent and checkout session.
     *
     * @param tenantId The tenant ID
     * @param paymentIntentId The Stripe payment intent ID
     * @param checkoutSessionId The Stripe checkout session ID (optional)
     * @return Stripe tax amount in dollars, or null if not found
     */
    public BigDecimal getStripeTax(String tenantId, String paymentIntentId, String checkoutSessionId) {
        String apiKey = getStripeApiKey(tenantId);
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Stripe API key not found for tenant: {}", tenantId);
            return null;
        }

        try {
            Stripe.apiKey = apiKey;

            // Method 1: Try CheckoutSession first (most reliable for Stripe Tax)
            if (checkoutSessionId != null && !checkoutSessionId.isEmpty()) {
                try {
                    log.debug("Retrieving CheckoutSession {} for tax data for tenant {}", checkoutSessionId, tenantId);
                    Session session = Session.retrieve(checkoutSessionId);

                    if (session.getTotalDetails() != null && session.getTotalDetails().getAmountTax() != null) {
                        Long taxInCents = session.getTotalDetails().getAmountTax();
                        BigDecimal taxAmount = BigDecimal.valueOf(taxInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        log.debug("Retrieved Stripe tax {} from CheckoutSession {} for tenant {}",
                            taxAmount, checkoutSessionId, tenantId);
                        return taxAmount;
                    }
                } catch (StripeException e) {
                    log.warn("Error retrieving CheckoutSession {} for tenant {}: {}",
                        checkoutSessionId, tenantId, e.getMessage());
                }
            }

            // Method 2: Try PaymentIntent metadata
            if (paymentIntentId != null && !paymentIntentId.isEmpty()) {
                try {
                    log.debug("Retrieving PaymentIntent {} metadata for tax data for tenant {}", paymentIntentId, tenantId);
                    PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

                    if (paymentIntent.getMetadata() != null && paymentIntent.getMetadata().containsKey("tax_amount")) {
                        String taxAmountStr = paymentIntent.getMetadata().get("tax_amount");
                        try {
                            BigDecimal taxAmount = new BigDecimal(taxAmountStr);
                            log.debug("Retrieved Stripe tax {} from PaymentIntent metadata for tenant {}",
                                taxAmount, tenantId);
                            return taxAmount;
                        } catch (NumberFormatException e) {
                            log.warn("Invalid tax_amount in PaymentIntent {} metadata for tenant {}: {}",
                                paymentIntentId, tenantId, taxAmountStr);
                        }
                    }
                } catch (StripeException e) {
                    log.warn("Error retrieving PaymentIntent {} for tenant {}: {}",
                        paymentIntentId, tenantId, e.getMessage());
                }
            }

            // No tax found
            log.debug("No tax found for PaymentIntent {} / CheckoutSession {} for tenant {}",
                paymentIntentId, checkoutSessionId, tenantId);
            return null;

        } catch (Exception e) {
            log.error("Unexpected error retrieving Stripe tax for tenant {}: {}", tenantId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get Stripe API secret key from PaymentProviderConfig for a tenant.
     * Decrypts the encrypted secret key using the encryption service.
     * Uses in-memory cache to avoid reading from database multiple times per batch job run.
     *
     * @param tenantId The tenant ID
     * @return Stripe API secret key (decrypted), or null if not found
     */
    private String getStripeApiKey(String tenantId) {
        // Check cache first (one-time read per tenant per batch job run)
        if (stripeApiKeyCache.containsKey(tenantId)) {
            log.debug("Using cached Stripe API key for tenant: {}", tenantId);
            return stripeApiKeyCache.get(tenantId);
        }

        try {
            Optional<PaymentProviderConfig> configOpt = paymentProviderConfigRepository
                .findByTenantIdAndProvider(tenantId, "STRIPE");

            if (configOpt.isEmpty()) {
                log.warn("No Stripe configuration found for tenant: {}", tenantId);
                // Cache null to avoid repeated database queries
                stripeApiKeyCache.put(tenantId, null);
                return null;
            }

            PaymentProviderConfig config = configOpt.get();

            // Get from encrypted field
            if (config.getProviderSecretKeyEncrypted() != null && !config.getProviderSecretKeyEncrypted().isEmpty()) {
                try {
                    String decryptedKey = encryptionService.decrypt(config.getProviderSecretKeyEncrypted());
                    log.debug("Successfully decrypted Stripe API key for tenant: {}", tenantId);
                    // Cache the decrypted key
                    stripeApiKeyCache.put(tenantId, decryptedKey);
                    return decryptedKey;
                } catch (Exception e) {
                    log.error("Failed to decrypt Stripe API key for tenant {}: {}", tenantId, e.getMessage(), e);
                    // Cache null to avoid repeated decryption attempts
                    stripeApiKeyCache.put(tenantId, null);
                    return null;
                }
            }

            log.warn("Stripe secret key not found in configuration for tenant: {}", tenantId);
            // Cache null to avoid repeated database queries
            stripeApiKeyCache.put(tenantId, null);
            return null;
        } catch (Exception e) {
            log.error("Error retrieving Stripe API key for tenant {}: {}", tenantId, e.getMessage(), e);
            // Cache null to avoid repeated database queries
            stripeApiKeyCache.put(tenantId, null);
            return null;
        }
    }

    /**
     * Clear the API key cache for a tenant (useful for testing or when config changes).
     *
     * @param tenantId The tenant ID
     */
    public void clearApiKeyCache(String tenantId) {
        stripeApiKeyCache.remove(tenantId);
        log.debug("Cleared Stripe API key cache for tenant: {}", tenantId);
    }

    /**
     * Clear all cached API keys (useful for testing or when configs change).
     */
    public void clearAllApiKeyCache() {
        stripeApiKeyCache.clear();
        log.debug("Cleared all Stripe API key cache");
    }
}
