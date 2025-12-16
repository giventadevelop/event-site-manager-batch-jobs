package com.eventmanager.batch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for decrypting encrypted payment provider credentials.
 * Uses AES-256-GCM encryption matching the backend implementation.
 *
 * Format: Base64(IV (12 bytes) + ciphertext (includes auth tag))
 * The encryption key is base64-encoded and must be decoded first.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int GCM_TAG_LENGTH = 16; // bytes (128 bits)

    // Support both PAYMENT_ENCRYPTION_KEY (env var) and application.payment.encryption.key (property)
    @Value("${PAYMENT_ENCRYPTION_KEY:${application.payment.encryption.key:}}")
    private String encryptionKeyBase64;

    /**
     * Decrypts an encrypted value using AES-256-GCM.
     * Matches the backend PaymentCredentialEncryptionService implementation.
     *
     * @param encryptedBase64 Base64-encoded encrypted value (format: IV + ciphertext)
     * @return Decrypted plaintext string
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            return null;
        }

        if (encryptionKeyBase64 == null || encryptionKeyBase64.isEmpty()) {
            throw new RuntimeException("PAYMENT_ENCRYPTION_KEY environment variable is not set. " +
                    "Required for decrypting payment provider credentials. " +
                    "The key should be base64-encoded.");
        }

        try {
            // Clean the encryption key - remove any escape characters (Windows backslash escaping)
            // Windows command line may add backslashes before special characters like =
            String cleanedKey = encryptionKeyBase64.replace("\\", "");

            log.debug("Encryption key (first 50 chars): {}, length: {}, cleaned length: {}",
                encryptionKeyBase64.length() > 50 ? encryptionKeyBase64.substring(0, 50) + "..." : encryptionKeyBase64,
                encryptionKeyBase64.length(), cleanedKey.length());

            // Decode the encryption key from base64 (as per backend implementation)
            byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);

            if (keyBytes.length != 32) {
                log.warn("Encryption key decoded to {} bytes, expected 32 bytes for AES-256", keyBytes.length);
            }

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Decode the encrypted value from base64
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);

            // Extract IV (first 12 bytes)
            if (encrypted.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted data too short to contain IV");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext (remaining bytes - includes auth tag)
            byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
            System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv); // 128 bits = 16 bytes * 8
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            // Base64 decode error - might be wrong key format
            log.error("Failed to decode encryption key or encrypted value. " +
                    "Ensure PAYMENT_ENCRYPTION_KEY is base64-encoded: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt: Invalid key or encrypted value format. " +
                    "Ensure PAYMENT_ENCRYPTION_KEY is base64-encoded.", e);
        } catch (Exception e) {
            log.error("Failed to decrypt value: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to decrypt encrypted value: " + e.getMessage(), e);
        }
    }
}

