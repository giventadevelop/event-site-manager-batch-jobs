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
        return buildEmailContent(template, null, null);
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
        log.debug("Building email content for template: id={}, name={}", template.getId(), template.getTemplateName());

        String subject = subjectOverride != null && !subjectOverride.isEmpty()
            ? subjectOverride
            : template.getSubject();
        String bodyHtml = bodyHtmlOverride != null && !bodyHtmlOverride.isEmpty()
            ? bodyHtmlOverride
            : template.getBodyHtml();

        // Build full HTML with header and footer images if available
        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

        // Add header image if available
        if (template.getHeaderImageUrl() != null && !template.getHeaderImageUrl().isEmpty()) {
            fullHtml.append("<div style='text-align: center; margin-bottom: 20px;'>");
            fullHtml.append("<img src='")
                .append(template.getHeaderImageUrl())
                .append("' alt='Header' style='max-width: 100%; height: auto;' />");
            fullHtml.append("</div>");
        }

        // Add body HTML
        fullHtml.append("<div>").append(bodyHtml).append("</div>");

        // Add footer HTML if available
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

        // Add tenant email footer HTML from tenant settings
        String tenantFooterHtml = getTenantEmailFooterHtml(template.getTenantId());
        if (tenantFooterHtml != null && !tenantFooterHtml.isEmpty()) {
            fullHtml.append("<div>").append(tenantFooterHtml).append("</div>");
        }

        fullHtml.append("</body></html>");

        Map<String, String> result = new HashMap<>();
        result.put("subject", subject);
        result.put("bodyHtml", fullHtml.toString());

        return result;
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

                    // Download footer HTML from S3
                    String footerHtml = "";
                    try {
                        footerHtml = s3Service.downloadHtmlFromUrl(emailFooterHtmlUrl);
                        if (footerHtml == null || footerHtml.isEmpty()) {
                            log.warn("Downloaded footer HTML is empty for tenant: {}", tenantId);
                            return "";
                        }
                        log.debug("Downloaded footer HTML from S3 for tenant: {}", tenantId);
                    } catch (Exception e) {
                        log.warn("Failed to download footer HTML from S3 for tenant {}: {}", tenantId, e.getMessage());
                        return "";
                    }

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
}

