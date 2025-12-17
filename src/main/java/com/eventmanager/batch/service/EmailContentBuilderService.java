package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.PromotionEmailTemplate;
import com.eventmanager.batch.domain.TenantSettings;
import com.eventmanager.batch.repository.TenantSettingsRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    // Cache for tenant footer HTML (per tenant, expires after 1 hour)
    private final Cache<String, String> footerHtmlCache = CacheBuilder
        .newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();

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
    private String getTenantEmailHeaderImageUrl(String tenantId) {
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
    private String getTenantEmailFooterHtml(String tenantId) {
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

                    // Check cache first
                    String cachedFooterHtml = footerHtmlCache.getIfPresent(cacheKey);
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
                    footerHtmlCache.put(cacheKey, footerHtml);
                    log.debug("Cached footer HTML for tenant: {}", tenantId);

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
                            footerHtmlCache.put(cacheKey, footerHtml);
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
}

