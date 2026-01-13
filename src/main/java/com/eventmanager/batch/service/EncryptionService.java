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
 * Service for decrypting encrypted payment provider keys.
 * Uses AES-256-GCM encryption with the encryption key from environment variable.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // 12 bytes for GCM

    @Value("${PAYMENT_ENCRYPTION_KEY:}")
    private String encryptionKey;

    /**
     * Decrypt an encrypted string using AES-256-GCM.
     *
     * @param encryptedValue The encrypted value (base64 encoded, format: iv:encrypted_data:tag)
     * @return Decrypted string
     * @throws Exception if decryption fails
     */
    public String decrypt(String encryptedValue) throws Exception {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            throw new IllegalArgumentException("Encrypted value cannot be null or empty");
        }

        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("Payment encryption key is not configured. Set PAYMENT_ENCRYPTION_KEY environment variable.");
        }

        try {
            // The encrypted value format is typically: base64(iv + encrypted_data + tag)
            // Format: base64 encoded string containing IV (12 bytes) + Ciphertext + Tag (16 bytes)
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedValue);

            // Extract IV (first 12 bytes), tag (last 16 bytes), and ciphertext (middle)
            if (encryptedBytes.length < GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException("Encrypted value is too short. Expected at least " + (GCM_IV_LENGTH + 16) + " bytes, got " + encryptedBytes.length);
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH);

            byte[] tag = new byte[16];
            System.arraycopy(encryptedBytes, encryptedBytes.length - 16, tag, 0, 16);

            byte[] ciphertext = new byte[encryptedBytes.length - GCM_IV_LENGTH - 16];
            System.arraycopy(encryptedBytes, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Decode encryption key (base64)
            // Remove any whitespace or escape characters that might be in the environment variable
            // Handle escaped equals signs and remove any invalid backslash characters
            String cleanKey = encryptionKey
                .trim()                              // Remove leading/trailing whitespace
                .replace("\\=", "=")                 // Replace escaped equals signs
                .replace("\\", "")                   // Remove any remaining backslash characters (invalid in Base64, ASCII 5c)
                .replaceAll("\\s", "");              // Remove any remaining whitespace

            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(cleanKey);
            } catch (IllegalArgumentException e) {
                log.error("Failed to decode encryption key (base64): {}", e.getMessage());
                throw new IllegalArgumentException("Invalid encryption key format (not valid base64): " + e.getMessage(), e);
            }

            if (keyBytes.length != 32) { // AES-256 requires 32 bytes (256 bits)
                throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits) after base64 decoding. Got " + keyBytes.length + " bytes");
            }

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Combine ciphertext and tag
            byte[] ciphertextWithTag = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
            System.arraycopy(tag, 0, ciphertextWithTag, ciphertext.length, tag.length);

            byte[] decryptedBytes = cipher.doFinal(ciphertextWithTag);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting value: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to decrypt value: " + e.getMessage(), e);
        }
    }
}

