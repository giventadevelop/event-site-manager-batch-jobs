package com.eventmanager.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with the backend API.
 * Handles JWT authentication and transaction updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackendApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${backend.api.base-url:http://localhost:8080}")
    private String backendApiBaseUrl;

    @Value("${backend.api.jwt.username:}")
    private String jwtUsername;

    @Value("${backend.api.jwt.password:}")
    private String jwtPassword;

    @Value("${backend.api.jwt.cache-ttl-seconds:3600}")
    private long jwtCacheTtlSeconds;

    private String cachedJwtToken;
    private Instant jwtTokenExpiry;

    /**
     * Get JWT token for backend API authentication.
     * Caches the token for the configured TTL period.
     */
    public String getJwtToken() {
        // Check if cached token is still valid
        if (cachedJwtToken != null && jwtTokenExpiry != null && Instant.now().isBefore(jwtTokenExpiry)) {
            log.debug("Using cached JWT token");
            return cachedJwtToken;
        }

        // Validate credentials are configured
        if (jwtUsername == null || jwtUsername.trim().isEmpty()) {
            log.error("JWT username is not configured. Please set backend.api.jwt.username or API_JWT_USER environment variable.");
            throw new IllegalStateException("JWT username is not configured. Please set backend.api.jwt.username or API_JWT_USER environment variable.");
        }
        if (jwtPassword == null || jwtPassword.trim().isEmpty()) {
            log.error("JWT password is not configured. Please set backend.api.jwt.password or API_JWT_PASS environment variable.");
            throw new IllegalStateException("JWT password is not configured. Please set backend.api.jwt.password or API_JWT_PASS environment variable.");
        }

        log.info("Authenticating with backend API to get JWT token");
        String url = backendApiBaseUrl + "/api/authenticate";

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("username", jwtUsername.trim());
            requestBody.put("password", jwtPassword.trim());
            requestBody.put("rememberMe", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                String token = null;

                // Try different possible field names for JWT token
                if (body.has("id_token")) {
                    token = body.get("id_token").asText();
                } else if (body.has("token")) {
                    token = body.get("token").asText();
                } else if (body.has("jwt")) {
                    token = body.get("jwt").asText();
                } else if (body.has("access_token")) {
                    token = body.get("access_token").asText();
                }

                if (token != null && !token.isEmpty()) {
                    cachedJwtToken = token;
                    jwtTokenExpiry = Instant.now().plusSeconds(jwtCacheTtlSeconds);
                    log.info("Successfully obtained JWT token, cached for {} seconds", jwtCacheTtlSeconds);
                    return token;
                } else {
                    log.error("JWT token not found in authentication response: {}", body);
                    throw new RuntimeException("JWT token not found in authentication response");
                }
            } else {
                log.error("Failed to authenticate with backend API: {}", response.getStatusCode());
                throw new RuntimeException("Failed to authenticate with backend API: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error during JWT authentication: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("JWT authentication failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Error during JWT authentication: {}", e.getMessage(), e);
            throw new RuntimeException("JWT authentication failed", e);
        }
    }

    /**
     * Update transaction with Stripe fee, tax, and net payout amounts.
     *
     * @param transactionId The transaction ID
     * @param stripeFeeAmount The Stripe fee amount (can be null)
     * @param stripeAmountTax The Stripe tax amount (can be null)
     * @param netPayoutAmount The net payout amount (can be null)
     * @return true if update was successful, false otherwise
     */
    public boolean updateTransaction(Long transactionId, java.math.BigDecimal stripeFeeAmount, java.math.BigDecimal stripeAmountTax, java.math.BigDecimal netPayoutAmount) {
        String url = backendApiBaseUrl + "/api/event-ticket-transactions/" + transactionId;
        String jwtToken = getJwtToken();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (stripeFeeAmount != null) {
                requestBody.put("stripeFeeAmount", stripeFeeAmount);
            }
            if (stripeAmountTax != null) {
                requestBody.put("stripeAmountTax", stripeAmountTax);
            }
            if (netPayoutAmount != null) {
                requestBody.put("netPayoutAmount", netPayoutAmount);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/merge-patch+json"));
            headers.setBearerAuth(jwtToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                request,
                Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Successfully updated transaction {} with fee: {}, tax: {}, netPayout: {}",
                    transactionId, stripeFeeAmount, stripeAmountTax, netPayoutAmount);
                return true;
            } else {
                log.warn("Unexpected status code when updating transaction {}: {}",
                    transactionId, response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            // JWT token expired, clear cache and retry once
            log.warn("JWT token expired, clearing cache and retrying");
            cachedJwtToken = null;
            jwtTokenExpiry = null;
            return updateTransaction(transactionId, stripeFeeAmount, stripeAmountTax, netPayoutAmount);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Transaction {} not found in backend: {}", transactionId, e.getResponseBodyAsString());
            return false;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error updating transaction {}: {} - {}",
                transactionId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error updating transaction {}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }
}
