package com.eventmanager.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for generating QR codes for donations.
 * Generates QR code URLs that can be used to verify donations at events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

    @Value("${qr.code.base-url:}")
    private String qrCodeBaseUrl;

    @Value("${qr.code.email-host-url-prefix:}")
    private String emailHostUrlPrefix;

    /**
     * Generate QR code URL for a donation.
     *
     * @param eventId the event ID
     * @param donationId the donation transaction ID
     * @param emailHostUrlPrefix the email host URL prefix (for QR code verification page)
     * @param type the QR code type (e.g., "DONATION")
     * @return the QR code URL
     */
    public String generateQrCode(Long eventId, Long donationId, String emailHostUrlPrefix, String type) {
        // Use provided emailHostUrlPrefix or fall back to configured value
        String effectiveUrlPrefix = emailHostUrlPrefix != null && !emailHostUrlPrefix.isEmpty()
            ? emailHostUrlPrefix
            : this.emailHostUrlPrefix;

        // If no URL prefix is configured, construct a basic URL
        if (effectiveUrlPrefix == null || effectiveUrlPrefix.isEmpty()) {
            effectiveUrlPrefix = qrCodeBaseUrl != null && !qrCodeBaseUrl.isEmpty()
                ? qrCodeBaseUrl
                : "https://app.example.com"; // Default fallback
        }

        // Generate QR code URL pointing to verification page
        // Format: {baseUrl}/verify/donation/{donationId}?eventId={eventId}
        String qrCodeUrl = String.format(
            "%s/verify/donation/%d?eventId=%d&type=%s",
            effectiveUrlPrefix,
            donationId,
            eventId,
            type
        );

        log.debug("Generated QR code URL for donation {}: {}", donationId, qrCodeUrl);
        return qrCodeUrl;
    }

    /**
     * Generate QR code image URL (for embedding in emails).
     * This typically points to an image service that generates QR code images.
     *
     * @param qrCodeUrl the QR code URL to encode
     * @return the QR code image URL
     */
    public String generateQrCodeImageUrl(String qrCodeUrl) {
        // Use a QR code image generation service
        // Format: {imageServiceUrl}/qr?data={encodedUrl}
        String imageServiceUrl = qrCodeBaseUrl != null && !qrCodeBaseUrl.isEmpty()
            ? qrCodeBaseUrl
            : "https://api.qrserver.com/v1/create-qr-code";

        try {
            String encodedUrl = java.net.URLEncoder.encode(qrCodeUrl, "UTF-8");
            String imageUrl = String.format("%s/?size=300x300&data=%s", imageServiceUrl, encodedUrl);
            log.debug("Generated QR code image URL: {}", imageUrl);
            return imageUrl;
        } catch (Exception e) {
            log.error("Failed to generate QR code image URL: {}", e.getMessage(), e);
            return qrCodeUrl; // Fallback to URL if image generation fails
        }
    }

    /**
     * Get email host URL prefix from configuration or environment.
     */
    public String getEmailHostUrlPrefix() {
        return emailHostUrlPrefix != null && !emailHostUrlPrefix.isEmpty()
            ? emailHostUrlPrefix
            : qrCodeBaseUrl;
    }
}
