package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.domain.TenantSettings;
import com.eventmanager.batch.dto.ContactFormEmailJobRequest;
import com.eventmanager.batch.dto.ManualPaymentConfirmationEmailJobRequest;
import com.eventmanager.batch.dto.ManualPaymentTicketEmailJobRequest;
import com.eventmanager.batch.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for building email content from templates.
 * Handles header/footer images and tenant-specific footer HTML.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailContentBuilderService {

    private final S3Service s3Service;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final CacheManager cacheManager;

    /**
     * Build email content from template.
     *
     * @param template the email template
     * @return map with "subject" and "bodyHtml" keys
     */
    public Map<String, String> buildEmailContent(PromotionEmailTemplate template) {
        return buildEmailContent(template, null, null, null);
    }

    /**
     * Build email content from template with optional overrides.
     *
     * @param template the email template
     * @param subjectOverride optional subject override
     * @param bodyHtmlOverride optional body HTML override
     * @return map with "subject" and "bodyHtml" keys
     */
    public Map<String, String> buildEmailContent(
        PromotionEmailTemplate template,
        String subjectOverride,
        String bodyHtmlOverride
    ) {
        return buildEmailContent(template, subjectOverride, bodyHtmlOverride, null);
    }

    /**
     * Build email content from template with fallback to tenant settings.
     *
     * @param template the email template
     * @param subjectOverride optional subject override
     * @param bodyHtmlOverride optional body HTML override
     * @param tenantIdForFallback the tenant ID to use for fallback to tenant settings
     * @return Map containing "subject" and "bodyHtml" keys
     */
    public Map<String, String> buildEmailContent(
        PromotionEmailTemplate template,
        String subjectOverride,
        String bodyHtmlOverride,
        String tenantIdForFallback
    ) {
        log.debug("Building email content for template: id={}, name={}, tenantIdForFallback={}",
            template.getId(), template.getTemplateName(), tenantIdForFallback);

        String subject = subjectOverride != null && !subjectOverride.isEmpty()
            ? subjectOverride
            : template.getSubject();
        String bodyHtml = bodyHtmlOverride != null && !bodyHtmlOverride.isEmpty()
            ? bodyHtmlOverride
            : template.getBodyHtml();

        // Build full HTML with header and footer images if available
        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Add header image - check template first, then fall back to tenant settings
        String headerImageUrl = template.getHeaderImageUrl();
        log.debug("Template header image URL: {}", headerImageUrl);
        if (headerImageUrl == null || headerImageUrl.isEmpty()) {
            // Fall back to tenant settings header image
            log.debug("Template has no header image, checking tenant settings for tenant: {}", tenantIdForFallback);
            headerImageUrl = getTenantEmailHeaderImageUrl(tenantIdForFallback);
            log.debug("Tenant settings header image URL: {}", headerImageUrl);
        }
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            log.debug("Adding header image to email: {}", headerImageUrl);
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>");
            fullHtml.append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />");
            fullHtml.append("</div>");
        } else {
            log.debug("No header image URL found for template or tenant");
        }

        // Add body HTML
        fullHtml.append("<div>").append(bodyHtml).append("</div>");

        // Add footer HTML if available (from template)
        if (template.getFooterHtml() != null && !template.getFooterHtml().isEmpty()) {
            fullHtml.append("<div>").append(template.getFooterHtml()).append("</div>");
        }

        // Add footer image if available (for backward compatibility)
        if (template.getFooterImageUrl() != null && !template.getFooterImageUrl().isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-top: 20px;'>");
            fullHtml.append("<img src='")
                .append(template.getFooterImageUrl())
                .append("' alt='Footer' style='max-width: 100%; height: auto;' />");
            fullHtml.append("</div>");
        }

        // Add tenant email footer HTML from tenant settings (FALLBACK)
        // Only add if template doesn't have footer HTML or footer image
        boolean templateHasFooter = (template.getFooterHtml() != null && !template.getFooterHtml().isEmpty()) ||
                                     (template.getFooterImageUrl() != null && !template.getFooterImageUrl().isEmpty());

        if (!templateHasFooter) {
            String tenantFooterHtml = getTenantEmailFooterHtml(tenantIdForFallback);
            if (tenantFooterHtml != null && !tenantFooterHtml.isEmpty()) {
                fullHtml.append("<div>").append(tenantFooterHtml).append("</div>");
            }
        }

        fullHtml.append("</body></html>");

        Map<String, String> result = new HashMap<>();
        result.put("subject", subject);
        result.put("bodyHtml", fullHtml.toString());

        return result;
    }

    /**
     * Get tenant email header image URL from tenant settings.
     *
     * @param tenantId the tenant ID
     * @return the header image URL, or empty string if not available
     */
    public String getTenantEmailHeaderImageUrl(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            log.debug("Tenant ID is null or empty, skipping header image");
            return "";
        }

        try {
            return tenantSettingsRepository
                .findByTenantId(tenantId)
                .map(tenantSettings -> {
                    String emailHeaderImageUrl = tenantSettings.getEmailHeaderImageUrl();
                    log.debug("Found tenant settings for tenant {}: emailHeaderImageUrl={}",
                        tenantId, emailHeaderImageUrl);
                    return emailHeaderImageUrl != null && !emailHeaderImageUrl.isEmpty()
                        ? emailHeaderImageUrl
                        : "";
                })
                .orElseGet(() -> {
                    log.debug("Tenant settings not found for tenant: {}", tenantId);
                    return "";
                });
        } catch (Exception e) {
            log.error("Error getting tenant email header image URL for tenant {}: {}",
                tenantId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Get tenant email footer HTML from tenant settings.
     * Downloads footer HTML from S3 and replaces {{LOGO_URL}} placeholder with tenant logo URL.
     * Uses caching to avoid repeated S3 downloads.
     *
     * @param tenantId the tenant ID
     * @return the footer HTML with logo URL replaced, or empty string if not available
     */
    public String getTenantEmailFooterHtml(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            log.debug("Tenant ID is null or empty, skipping tenant footer HTML");
            return "";
        }

        try {
            return tenantSettingsRepository
                .findByTenantId(tenantId)
                .map(tenantSettings -> {
                    String emailFooterHtmlUrl = tenantSettings.getEmailFooterHtmlUrl();
                    String logoImageUrl = tenantSettings.getLogoImageUrl();

                    // If no footer HTML URL is configured, return empty string
                    if (emailFooterHtmlUrl == null || emailFooterHtmlUrl.isEmpty()) {
                        log.debug("Email footer HTML URL not configured for tenant: {}", tenantId);
                        return "";
                    }

                    // Create cache key including tenantId and logoImageUrl to ensure correct caching
                    // when logo changes, we get fresh footer HTML
                    String cacheKey = "footer:" + tenantId + "|" + (logoImageUrl != null ? logoImageUrl : "");

                    Cache cache = cacheManager.getCache("tenantFooterHtmlCache");
                    String cachedFooterHtml = null;
                    if (cache != null) {
                        cachedFooterHtml = cache.get(cacheKey, String.class);
                    }
                    if (cachedFooterHtml != null) {
                        log.debug("Cache hit for footer HTML for tenant: {}", tenantId);
                        return cachedFooterHtml;
                    }

                    log.debug("Cache miss for footer HTML for tenant: {}, fetching from S3", tenantId);

                    // Download footer HTML from S3 with retry logic
                    // Retry up to 3 times with exponential backoff (1s, 2s, 4s)
                    String footerHtml = s3Service.downloadHtmlFromUrlWithRetry(emailFooterHtmlUrl, 3, 1000);
                    if (footerHtml == null || footerHtml.isEmpty()) {
                        log.warn("Downloaded footer HTML is empty for tenant: {} after retries", tenantId);
                        return "";
                    }
                    log.debug("Downloaded footer HTML from S3 for tenant: {}", tenantId);

                    // Replace {{LOGO_URL}} placeholder with tenant logo URL if available
                    if (logoImageUrl != null && !logoImageUrl.isEmpty()) {
                        footerHtml = footerHtml.replace("{{LOGO_URL}}", logoImageUrl);
                        log.debug("Replaced {{LOGO_URL}} placeholder with logo URL for tenant: {}", tenantId);
                    } else {
                        log.debug("Logo image URL not configured for tenant: {}, leaving {{LOGO_URL}} placeholder as is", tenantId);
                    }

                    // Cache the processed footer HTML
                    if (cache != null) {
                        cache.put(cacheKey, footerHtml);
                        log.debug("Cached footer HTML for tenant: {}", tenantId);
                    }

                    return footerHtml;
                })
                .orElseGet(() -> {
                    log.debug("Tenant settings not found for tenant: {}", tenantId);
                    return "";
                });
        } catch (Exception e) {
            log.error("Error getting tenant email footer HTML for tenant {}: {}", tenantId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Build HTML body for the main contact form email using tenant branding.
     */
    public String buildContactEmailBody(ContactFormEmailJobRequest request) {
        String tenantId = request.getTenantId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Header image
        String headerImageUrl = getTenantEmailHeaderImageUrl(tenantId);
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>")
                .append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />")
                .append("</div>");
        }

        // Body content
        fullHtml.append("<div>");
        fullHtml.append("<p><strong>New contact form submission</strong></p>");
        fullHtml.append("<p><strong>Name:</strong> ")
            .append(escapeHtml(request.getFirstName()))
            .append(" ")
            .append(escapeHtml(request.getLastName()))
            .append("</p>");
        fullHtml.append("<p><strong>Email:</strong> ")
            .append(escapeHtml(request.getFromEmail()))
            .append("</p>");
        fullHtml.append("<p><strong>Message:</strong><br/>")
            .append(escapeHtml(request.getMessageBody()).replace("\n", "<br/>"))
            .append("</p>");
        fullHtml.append("</div>");

        // Footer HTML
        String footerHtml = getTenantEmailFooterHtml(tenantId);
        if (footerHtml != null && !footerHtml.isEmpty()) {
            fullHtml.append("<div>").append(footerHtml).append("</div>");
        }

        fullHtml.append("</body></html>");
        return fullHtml.toString();
    }

    /**
     * Build HTML body for the visitor confirmation email using tenant branding.
     */
    public String buildContactConfirmationEmailBody(ContactFormEmailJobRequest request) {
        String tenantId = request.getTenantId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Header image
        String headerImageUrl = getTenantEmailHeaderImageUrl(tenantId);
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>")
                .append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />")
                .append("</div>");
        }

        // Body content
        fullHtml.append("<div>");
        fullHtml.append("<p>Dear ")
            .append(escapeHtml(request.getFirstName()))
            .append(" ")
            .append(escapeHtml(request.getLastName()))
            .append(",</p>");
        fullHtml.append("<p>We have received your message and will get back to you as soon as possible.</p>");
        fullHtml.append("<p><strong>Your message details:</strong></p>");
        fullHtml.append("<p><strong>Email:</strong> ")
            .append(escapeHtml(request.getFromEmail()))
            .append("</p>");
        fullHtml.append("<p><strong>Message:</strong><br/>")
            .append(escapeHtml(request.getMessageBody()).replace("\n", "<br/>"))
            .append("</p>");
        fullHtml.append("</div>");

        // Footer HTML
        String footerHtml = getTenantEmailFooterHtml(tenantId);
        if (footerHtml != null && !footerHtml.isEmpty()) {
            fullHtml.append("<div>").append(footerHtml).append("</div>");
        }

        fullHtml.append("</body></html>");
        return fullHtml.toString();
    }

    /**
     * Basic HTML escaping for user-provided content.
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /**
     * Pre-fetch and validate tenant email header and footer before batch processing.
     * Attempts to download header image URL and footer HTML with retries to ensure they are ready.
     *
     * @param tenantId the tenant ID
     * @param maxWaitTimeSeconds maximum time to wait for downloads to complete (in seconds)
     * @return true if header and/or footer are ready, false if both are unavailable after retries
     */
    public boolean ensureHeaderAndFooterReady(String tenantId, int maxWaitTimeSeconds) {
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("Tenant ID is null or empty, cannot ensure header/footer are ready");
            return false;
        }

        log.info("Ensuring header and footer are ready for tenant: {} (max wait: {}s)", tenantId, maxWaitTimeSeconds);

        AtomicBoolean headerReady = new AtomicBoolean(false);
        AtomicBoolean footerReady = new AtomicBoolean(false);

        try {
            return tenantSettingsRepository
                .findByTenantId(tenantId)
                .map(tenantSettings -> {
                    String emailHeaderImageUrl = tenantSettings.getEmailHeaderImageUrl();
                    String emailFooterHtmlUrl = tenantSettings.getEmailFooterHtmlUrl();
                    String logoImageUrl = tenantSettings.getLogoImageUrl();

                    // Check header image URL (just validate it exists, no download needed for images)
                    if (emailHeaderImageUrl != null && !emailHeaderImageUrl.isEmpty()) {
                        headerReady.set(true);
                        log.info("Header image URL configured for tenant: {}", tenantId);
                    } else {
                        log.debug("No header image URL configured for tenant: {}", tenantId);
                    }

                    // Pre-fetch footer HTML with retries
                    if (emailFooterHtmlUrl != null && !emailFooterHtmlUrl.isEmpty()) {
                        log.info("Pre-fetching footer HTML for tenant: {} from URL: {}", tenantId, emailFooterHtmlUrl);

                        // Use retry logic: 5 retries with 2 second initial delay (total ~62 seconds max)
                        // But cap at maxWaitTimeSeconds
                        int maxRetries = Math.min(5, (int)(maxWaitTimeSeconds / 2));
                        long initialDelay = 2000; // 2 seconds

                        String footerHtml = s3Service.downloadHtmlFromUrlWithRetry(
                            emailFooterHtmlUrl,
                            maxRetries,
                            initialDelay
                        );

                        if (footerHtml != null && !footerHtml.isEmpty()) {
                            // Replace {{LOGO_URL}} placeholder if logo is available
                            if (logoImageUrl != null && !logoImageUrl.isEmpty()) {
                                footerHtml = footerHtml.replace("{{LOGO_URL}}", logoImageUrl);
                            }

                            // Cache the processed footer HTML
                            String cacheKey = "footer:" + tenantId + "|" + (logoImageUrl != null ? logoImageUrl : "");
                            Cache cache = cacheManager.getCache("tenantFooterHtmlCache");
                            if (cache != null) {
                                cache.put(cacheKey, footerHtml);
                            }
                            footerReady.set(true);
                            log.info("Successfully pre-fetched and cached footer HTML for tenant: {}", tenantId);
                        } else {
                            log.warn("Failed to pre-fetch footer HTML for tenant: {} after retries", tenantId);
                        }
                    } else {
                        log.debug("No footer HTML URL configured for tenant: {}", tenantId);
                    }

                    // Return true if at least one (header or footer) is ready, or if neither is configured
                    boolean result = headerReady.get() || footerReady.get() ||
                                   (emailHeaderImageUrl == null || emailHeaderImageUrl.isEmpty()) &&
                                   (emailFooterHtmlUrl == null || emailFooterHtmlUrl.isEmpty());

                    if (result) {
                        log.info("Header/footer readiness check completed for tenant: {} - Header: {}, Footer: {}",
                            tenantId, headerReady.get(), footerReady.get());
                    } else {
                        log.warn("Header and footer are not ready for tenant: {} after retries, but proceeding anyway", tenantId);
                    }

                    return result;
                })
                .orElseGet(() -> {
                    log.warn("Tenant settings not found for tenant: {}, cannot ensure header/footer are ready", tenantId);
                    return false;
                });
        } catch (Exception e) {
            log.error("Error ensuring header and footer are ready for tenant {}: {}", tenantId, e.getMessage(), e);
            // Return false but don't block processing - let it proceed and handle errors gracefully
            return false;
        }
    }

    /**
     * Build HTML body for manual payment confirmation email.
     * Includes payment request details, ticket summary, and payment instructions.
     */
    public String buildManualPaymentConfirmationEmailBody(ManualPaymentConfirmationEmailJobRequest request) {
        String tenantId = request.getTenantId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Header image
        String headerImageUrl = getTenantEmailHeaderImageUrl(tenantId);
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>")
                .append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />")
                .append("</div>");
        }

        // Body content
        fullHtml.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");

        fullHtml.append("<h1 style='color: #8b7d6b;'>Payment Request Received</h1>");

        fullHtml.append("<p>Dear ").append(escapeHtml(request.getRecipientName())).append(",</p>");

        fullHtml.append("<p>Thank you for your interest in <strong>")
            .append(escapeHtml(request.getEventTitle()))
            .append("</strong>");
        if (request.getEventDate() != null && !request.getEventDate().isEmpty()) {
            fullHtml.append(" on ").append(escapeHtml(request.getEventDate()));
        }
        if (request.getEventTime() != null && !request.getEventTime().isEmpty()) {
            fullHtml.append(" at ").append(escapeHtml(request.getEventTime()));
        }
        fullHtml.append(". We have received your payment request and it is currently pending confirmation.</p>");

        // Payment Request Details
        fullHtml.append("<h2 style='color: #1f4c8f;'>Payment Request Details</h2>");
        fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
        if (request.getTransactionReference() != null && !request.getTransactionReference().isEmpty()) {
            fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Transaction Reference:</strong></td>")
                .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                .append(escapeHtml(request.getTransactionReference()))
                .append("</td></tr>");
        }
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Payment Method:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
            .append(escapeHtml(request.getPaymentMethod()))
            .append("</td></tr>");
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Total Amount:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>$")
            .append(escapeHtml(request.getAmount().toString()))
            .append("</td></tr>");
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Status:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'><span style='color: #d97706;'>Pending Confirmation</span></td></tr>");
        fullHtml.append("</table>");

        // Ticket Summary
        if (request.getTicketSummary() != null && !request.getTicketSummary().isEmpty()) {
            fullHtml.append("<h2 style='color: #1f4c8f;'>Ticket Summary</h2>");
            fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
            fullHtml.append("<thead><tr style='background-color: #f8f9fa;'>")
                .append("<th style='padding: 10px; text-align: left; border-bottom: 2px solid #ddd;'>Ticket Type</th>")
                .append("<th style='padding: 10px; text-align: center; border-bottom: 2px solid #ddd;'>Quantity</th>")
                .append("<th style='padding: 10px; text-align: right; border-bottom: 2px solid #ddd;'>Price</th>")
                .append("<th style='padding: 10px; text-align: right; border-bottom: 2px solid #ddd;'>Total</th>")
                .append("</tr></thead><tbody>");

            for (ManualPaymentConfirmationEmailJobRequest.TicketSummaryItem item : request.getTicketSummary()) {
                fullHtml.append("<tr>")
                    .append("<td style='padding: 10px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(item.getTicketTypeName()))
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: center; border-bottom: 1px solid #ddd;'>")
                    .append(item.getQuantity())
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: right; border-bottom: 1px solid #ddd;'>$")
                    .append(escapeHtml(item.getPricePerUnit().toString()))
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: right; border-bottom: 1px solid #ddd;'>$")
                    .append(escapeHtml(item.getTotalAmount().toString()))
                    .append("</td>")
                    .append("</tr>");
            }

            fullHtml.append("<tr style='font-weight: bold;'>")
                .append("<td colspan='3' style='padding: 10px; text-align: right; border-top: 2px solid #ddd;'>Total:</td>")
                .append("<td style='padding: 10px; text-align: right; border-top: 2px solid #ddd;'>$")
                .append(escapeHtml(request.getAmount().toString()))
                .append("</td>")
                .append("</tr>");
            fullHtml.append("</tbody></table>");
        }

        // Payment Instructions
        if (request.getPaymentHandle() != null || request.getPaymentInstructions() != null) {
            fullHtml.append("<h2 style='color: #1f4c8f;'>Payment Instructions</h2>");
            fullHtml.append("<div style='background-color: #f8f9fa; padding: 15px; border-radius: 8px; margin: 20px 0;'>");
            fullHtml.append("<p><strong>Please send payment via ").append(escapeHtml(request.getPaymentMethod())).append(":</strong></p>");
            fullHtml.append("<ul>");
            if (request.getPaymentHandle() != null && !request.getPaymentHandle().isEmpty()) {
                fullHtml.append("<li><strong>Recipient:</strong> ").append(escapeHtml(request.getPaymentHandle())).append("</li>");
            }
            fullHtml.append("<li><strong>Amount:</strong> $").append(escapeHtml(request.getAmount().toString())).append("</li>");
            if (request.getPaymentInstructions() != null && !request.getPaymentInstructions().isEmpty()) {
                fullHtml.append("<li><strong>Memo/Note:</strong> ").append(escapeHtml(request.getPaymentInstructions())).append("</li>");
            }
            fullHtml.append("</ul>");
            fullHtml.append("</div>");
        }

        // Next Steps
        fullHtml.append("<h2 style='color: #1f4c8f;'>Next Steps</h2>");
        fullHtml.append("<p>Once you complete your payment, our team will verify the receipt and confirm your tickets. ")
            .append("You will receive a separate email with your tickets and QR code once payment is confirmed.</p>");

        fullHtml.append("<p><strong>Important:</strong> This is a payment request only. Your tickets will be issued after payment confirmation.</p>");

        fullHtml.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 30px 0;' />");

        fullHtml.append("<p style='font-size: 0.9em; color: #666;'>")
            .append("If you have any questions, please contact us at [support email].<br/>")
            .append("Event: <a href='#'>").append(escapeHtml(request.getEventTitle())).append("</a>")
            .append("</p>");

        fullHtml.append("</div>");

        // Footer HTML
        String footerHtml = getTenantEmailFooterHtml(tenantId);
        if (footerHtml != null && !footerHtml.isEmpty()) {
            fullHtml.append("<div>").append(footerHtml).append("</div>");
        }

        fullHtml.append("</body></html>");
        return fullHtml.toString();
    }

    /**
     * Build HTML body for manual payment ticket email.
     * Includes QR code, ticket details, and event information.
     */
    public String buildManualPaymentTicketEmailBody(ManualPaymentTicketEmailJobRequest request) {
        String tenantId = request.getTenantId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Header image
        String headerImageUrl = getTenantEmailHeaderImageUrl(tenantId);
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>")
                .append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />")
                .append("</div>");
        }

        // Body content
        fullHtml.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");

        fullHtml.append("<h1 style='color: #8b7d6b;'>Your Tickets</h1>");

        fullHtml.append("<p>Dear ").append(escapeHtml(request.getRecipientName())).append(",</p>");

        fullHtml.append("<p><strong>Your payment has been confirmed! Your tickets are ready.</strong></p>");

        fullHtml.append("<p>Thank you for your purchase for <strong>")
            .append(escapeHtml(request.getEventTitle()))
            .append("</strong>");
        if (request.getEventDate() != null && !request.getEventDate().isEmpty()) {
            fullHtml.append(" on ").append(escapeHtml(request.getEventDate()));
        }
        if (request.getEventTime() != null && !request.getEventTime().isEmpty()) {
            fullHtml.append(" at ").append(escapeHtml(request.getEventTime()));
        }
        fullHtml.append(".</p>");

        // QR Code Display
        if (request.getQrCodeImageUrl() != null && !request.getQrCodeImageUrl().isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin: 30px 0;'>");
            fullHtml.append("<img src='")
                .append(escapeHtml(request.getQrCodeImageUrl()))
                .append("' alt='QR Code for ")
                .append(escapeHtml(request.getEventTitle()))
                .append(" - ")
                .append(escapeHtml(request.getTransactionReference()))
                .append("' style='max-width: 300px; height: auto; border: 2px solid #ddd; padding: 10px; background-color: #fff;' />");
            fullHtml.append("<p style='margin-top: 10px; font-weight: bold;'>Present this QR code at the event for entry</p>");
            fullHtml.append("</div>");
        }

        // Ticket Details
        fullHtml.append("<h2 style='color: #1f4c8f;'>Ticket Details</h2>");
        fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
        if (request.getTransactionReference() != null && !request.getTransactionReference().isEmpty()) {
            fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Transaction Reference:</strong></td>")
                .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                .append(escapeHtml(request.getTransactionReference()))
                .append("</td></tr>");
        }
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Payment Status:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'><span style='color: #059669; font-weight: bold;'>Confirmed</span></td></tr>");
        fullHtml.append("</table>");

        // Ticket Items
        if (request.getTicketItems() != null && !request.getTicketItems().isEmpty()) {
            fullHtml.append("<h2 style='color: #1f4c8f;'>Ticket Summary</h2>");
            fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
            fullHtml.append("<thead><tr style='background-color: #f8f9fa;'>")
                .append("<th style='padding: 10px; text-align: left; border-bottom: 2px solid #ddd;'>Ticket Type</th>")
                .append("<th style='padding: 10px; text-align: center; border-bottom: 2px solid #ddd;'>Quantity</th>")
                .append("<th style='padding: 10px; text-align: right; border-bottom: 2px solid #ddd;'>Price</th>")
                .append("<th style='padding: 10px; text-align: right; border-bottom: 2px solid #ddd;'>Total</th>")
                .append("</tr></thead><tbody>");

            for (ManualPaymentTicketEmailJobRequest.TicketItem item : request.getTicketItems()) {
                fullHtml.append("<tr>")
                    .append("<td style='padding: 10px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(item.getTicketTypeName()))
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: center; border-bottom: 1px solid #ddd;'>")
                    .append(item.getQuantity())
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: right; border-bottom: 1px solid #ddd;'>$")
                    .append(escapeHtml(item.getPricePerUnit().toString()))
                    .append("</td>")
                    .append("<td style='padding: 10px; text-align: right; border-bottom: 1px solid #ddd;'>$")
                    .append(escapeHtml(item.getTotalAmount().toString()))
                    .append("</td>")
                    .append("</tr>");
            }

            fullHtml.append("<tr style='font-weight: bold;'>")
                .append("<td colspan='3' style='padding: 10px; text-align: right; border-top: 2px solid #ddd;'>Total:</td>")
                .append("<td style='padding: 10px; text-align: right; border-top: 2px solid #ddd;'>$")
                .append(escapeHtml(request.getTotalAmount().toString()))
                .append("</td>")
                .append("</tr>");
            fullHtml.append("</tbody></table>");
        }

        // Event Information
        if (request.getEventLocation() != null || request.getEventAddress() != null) {
            fullHtml.append("<h2 style='color: #1f4c8f;'>Event Information</h2>");
            fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
            if (request.getEventDate() != null && !request.getEventDate().isEmpty()) {
                fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Date:</strong></td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(request.getEventDate()))
                    .append("</td></tr>");
            }
            if (request.getEventTime() != null && !request.getEventTime().isEmpty()) {
                fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Time:</strong></td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(request.getEventTime()))
                    .append("</td></tr>");
            }
            if (request.getEventLocation() != null && !request.getEventLocation().isEmpty()) {
                fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Location:</strong></td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(request.getEventLocation()))
                    .append("</td></tr>");
            }
            if (request.getEventAddress() != null && !request.getEventAddress().isEmpty()) {
                fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Address:</strong></td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                    .append(escapeHtml(request.getEventAddress()))
                    .append("</td></tr>");
            }
            fullHtml.append("</table>");
        }

        // Important Instructions
        fullHtml.append("<h2 style='color: #1f4c8f;'>Important Instructions</h2>");
        fullHtml.append("<ul>");
        fullHtml.append("<li>Please bring this QR code to the event.</li>");
        fullHtml.append("<li>Each attendee must have their own QR code (if multiple tickets).</li>");
        fullHtml.append("<li>QR code is valid for entry only.</li>");
        fullHtml.append("</ul>");

        fullHtml.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 30px 0;' />");

        fullHtml.append("<p style='font-size: 0.9em; color: #666;'>")
            .append("Event organizer contact information<br/>")
            .append("Event: <a href='#'>").append(escapeHtml(request.getEventTitle())).append("</a>")
            .append("</p>");

        fullHtml.append("</div>");

        // Footer HTML
        String footerHtml = getTenantEmailFooterHtml(tenantId);
        if (footerHtml != null && !footerHtml.isEmpty()) {
            fullHtml.append("<div>").append(footerHtml).append("</div>");
        }

        fullHtml.append("</body></html>");
        return fullHtml.toString();
    }

    /**
     * Build HTML body for donation confirmation email.
     * Includes donation details, event information, and QR code if available.
     */
    public String buildDonationConfirmationEmailBody(
        Long eventId,
        Long donationId,
        String transactionReference,
        java.math.BigDecimal amount,
        String recipientEmail,
        String firstName,
        String lastName,
        String eventTitle,
        String qrCodeImageUrl,
        String tenantId
    ) {
        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Header image
        String headerImageUrl = getTenantEmailHeaderImageUrl(tenantId);
        if (headerImageUrl != null && !headerImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>")
                .append("<img src='")
                .append(headerImageUrl)
                .append("' alt='Header' style='max-width: 100%; height: auto;' />")
                .append("</div>");
        }

        // Body content
        fullHtml.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");

        fullHtml.append("<h1 style='color: #8b7d6b;'>Thank You for Your Donation!</h1>");

        String recipientName = (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty())
            ? firstName + " " + lastName
            : (firstName != null && !firstName.isEmpty() ? firstName : "Valued Donor");

        fullHtml.append("<p>Dear ").append(escapeHtml(recipientName)).append(",</p>");

        fullHtml.append("<p>We are deeply grateful for your generous donation");
        if (eventTitle != null && !eventTitle.isEmpty()) {
            fullHtml.append(" to <strong>").append(escapeHtml(eventTitle)).append("</strong>");
        }
        fullHtml.append(". Your support makes a meaningful difference and helps us continue our mission.</p>");

        // Donation Details
        fullHtml.append("<h2 style='color: #1f4c8f;'>Donation Details</h2>");
        fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
        if (transactionReference != null && !transactionReference.isEmpty()) {
            fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Transaction Reference:</strong></td>")
                .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'>")
                .append(escapeHtml(transactionReference))
                .append("</td></tr>");
        }
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Donation Amount:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong style='color: #059669; font-size: 1.2em;'>$")
            .append(escapeHtml(amount.toString()))
            .append("</strong></td></tr>");
        fullHtml.append("<tr><td style='padding: 8px; border-bottom: 1px solid #ddd;'><strong>Status:</strong></td>")
            .append("<td style='padding: 8px; border-bottom: 1px solid #ddd;'><span style='color: #059669; font-weight: bold;'>Confirmed</span></td></tr>");
        fullHtml.append("</table>");

        // QR Code Display (if available)
        if (qrCodeImageUrl != null && !qrCodeImageUrl.isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin: 30px 0;'>");
            fullHtml.append("<img src='")
                .append(escapeHtml(qrCodeImageUrl))
                .append("' alt='QR Code for Donation")
                .append(eventTitle != null ? " - " + escapeHtml(eventTitle) : "")
                .append("' style='max-width: 300px; height: auto; border: 2px solid #ddd; padding: 10px; background-color: #fff;' />");
            fullHtml.append("<p style='margin-top: 10px; font-weight: bold;'>Present this QR code at the event for verification</p>");
            fullHtml.append("</div>");
        }

        // Event Information (if available)
        if (eventTitle != null && !eventTitle.isEmpty()) {
            fullHtml.append("<h2 style='color: #1f4c8f;'>Event Information</h2>");
            fullHtml.append("<p><strong>").append(escapeHtml(eventTitle)).append("</strong></p>");
        }

        // Tax Receipt Information
        fullHtml.append("<h2 style='color: #1f4c8f;'>Tax Receipt</h2>");
        fullHtml.append("<p>This email serves as your donation receipt. Please keep this email for your records.</p>");
        fullHtml.append("<p><strong>Donation Date:</strong> ").append(java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy"))).append("</p>");
        fullHtml.append("<p><strong>Donation Amount:</strong> $").append(escapeHtml(amount.toString())).append("</p>");

        fullHtml.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 30px 0;' />");

        fullHtml.append("<p style='font-size: 0.9em; color: #666;'>")
            .append("If you have any questions about your donation, please contact us.<br/>");
        if (eventTitle != null && !eventTitle.isEmpty()) {
            fullHtml.append("Event: <a href='#'>").append(escapeHtml(eventTitle)).append("</a>");
        }
        fullHtml.append("</p>");

        fullHtml.append("</div>");

        // Footer HTML
        String footerHtml = getTenantEmailFooterHtml(tenantId);
        if (footerHtml != null && !footerHtml.isEmpty()) {
            fullHtml.append("<div>").append(footerHtml).append("</div>");
        }

        fullHtml.append("</body></html>");
        return fullHtml.toString();
    }
}

