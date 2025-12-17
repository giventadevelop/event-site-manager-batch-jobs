# Tenant Email Header and Footer Fallback Implementation Prompt

## Context
You are working on the **Event Site Manager Batch Jobs** project located at `E:\project_workspace\event-site-manager-batch-jobs`. This project receives batch email job requests from the backend Spring Boot application (`malayalees-us-site-boot`) and processes them asynchronously.

## Problem Statement
Currently, when building email content from promotion email templates, if the template doesn't have a header image or footer configured, the emails are sent without tenant branding (header images and footers). We need to implement a **fallback mechanism** that automatically uses tenant-level email header and footer settings from the `tenant_settings` table when template-level settings are not available.

## Requirements

### 1. Fallback Priority Order
When building email content, use the following priority:

**For Header Image:**
1. **First Priority**: Use `PromotionEmailTemplate.headerImageUrl` if it exists and is not empty
2. **Fallback**: Use `TenantSettings.emailHeaderImageUrl` from tenant settings

**For Footer:**
1. **First Priority**: Use `PromotionEmailTemplate.footerHtml` if it exists and is not empty
2. **Second Priority**: Use `PromotionEmailTemplate.footerImageUrl` if it exists and is not empty (for backward compatibility)
3. **Fallback**: Use `TenantSettings.emailFooterHtmlUrl` - download HTML from S3, replace `{{LOGO_URL}}` placeholder with `TenantSettings.logoImageUrl`

### 2. Implementation Details

#### Database Schema Reference
The `tenant_settings` table contains:
- `email_header_image_url` (VARCHAR 2048) - URL to tenant's email header image
- `email_footer_html_url` (VARCHAR 2048) - S3 URL to tenant's email footer HTML template
- `logo_image_url` (VARCHAR 2048) - URL to tenant's logo (used to replace {{LOGO_URL}} in footer)

#### Key Implementation Points

1. **Tenant ID Source**: The `tenantId` is provided in the `BatchJobEmailRequest` DTO. Always use this `tenantId` parameter for fallback lookups, NOT the template's tenantId (to ensure correct tenant context in batch processing).

2. **Footer HTML Processing**:
   - Download footer HTML from S3 using the `emailFooterHtmlUrl`
   - Replace `{{LOGO_URL}}` placeholder with the actual `logoImageUrl` from tenant settings
   - Implement caching (1 hour TTL) to avoid repeated S3 downloads
   - Cache key should include both `tenantId` and `logoImageUrl` to invalidate when logo changes

3. **Error Handling**:
   - If tenant settings are not found, gracefully skip header/footer (return empty string)
   - If S3 download fails, log warning and skip footer (return empty string)
   - Never throw exceptions that would break email sending

## Implementation Pattern

### Step 1: Update buildEmailContent Method

Modify your `buildEmailContent` method to accept an explicit `tenantId` parameter for fallback:

```java
/**
 * Build email content from template with fallback to tenant settings.
 *
 * @param template the email template
 * @param subjectOverride optional subject override
 * @param bodyHtmlOverride optional body HTML override
 * @param tenantIdForFallback the tenant ID to use for fallback to tenant settings
 * @return Map containing "subject" and "bodyHtml" keys
 */
private Map<String, String> buildEmailContent(
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
```

### Step 2: Implement getTenantEmailHeaderImageUrl Method

```java
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
```

### Step 3: Implement getTenantEmailFooterHtml Method with Caching

```java
// Add cache field to your service class
private final Cache<String, String> footerHtmlCache = CacheBuilder
    .newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

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
        // Fetch tenant settings
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
                    log.warn("Failed to download footer HTML from S3 for tenant {}: {}",
                        tenantId, e.getMessage());
                    return "";
                }

                // Replace {{LOGO_URL}} placeholder with tenant logo URL if available
                if (logoImageUrl != null && !logoImageUrl.isEmpty()) {
                    footerHtml = footerHtml.replace("{{LOGO_URL}}", logoImageUrl);
                    log.debug("Replaced {{LOGO_URL}} placeholder with logo URL for tenant: {}", tenantId);
                } else {
                    log.debug("Logo image URL not configured for tenant: {}, leaving {{LOGO_URL}} placeholder as is",
                        tenantId);
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
        log.error("Error getting tenant email footer HTML for tenant {}: {}",
            tenantId, e.getMessage(), e);
        return "";
    }
}
```

### Step 4: Update Method Calls to Use tenantId Parameter

Ensure that when you call `buildEmailContent`, you pass the `tenantId` from the `BatchJobEmailRequest`:

```java
// Example: In your batch email processing method
public void processEmailBatch(BatchJobEmailRequest request) {
    String tenantId = request.getTenantId(); // Get from request
    Long templateId = request.getTemplateId();

    // Fetch template
    PromotionEmailTemplate template = templateRepository.findById(templateId)
        .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

    // Build email content with tenantId for fallback
    Map<String, String> emailContent = buildEmailContent(
        template,
        null, // subjectOverride
        null, // bodyHtmlOverride
        tenantId // Use tenantId from request for fallback
    );

    String subject = emailContent.get("subject");
    String bodyHtml = emailContent.get("bodyHtml");

    // Send email...
}
```

## Required Dependencies

Make sure you have these dependencies in your `pom.xml`:

```xml
<!-- Google Guava for caching -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>

<!-- S3 Service (if not already included) -->
<!-- Your existing S3 service dependency -->
```

## Required Repositories/Services

You'll need access to:
1. `TenantSettingsRepository` - to query tenant settings by tenantId
2. `S3Service` - to download footer HTML from S3 URLs
3. `PromotionEmailTemplateRepository` - to fetch email templates

## Database Query

The tenant settings lookup query:
```sql
SELECT email_header_image_url, email_footer_html_url, logo_image_url
FROM tenant_settings
WHERE tenant_id = ?
```

## Testing Checklist

After implementation, verify:

1. ✅ **Template has header image** → Uses template header image (no fallback)
2. ✅ **Template has NO header image, tenant has header image** → Uses tenant header image (fallback works)
3. ✅ **Template has footer HTML** → Uses template footer (no fallback)
4. ✅ **Template has NO footer, tenant has footer HTML URL** → Downloads and uses tenant footer with logo replacement (fallback works)
5. ✅ **Tenant settings not found** → Gracefully skips header/footer (no errors)
6. ✅ **S3 download fails** → Logs warning, skips footer, continues email sending
7. ✅ **Cache works** → Second call for same tenant uses cached footer HTML
8. ✅ **Logo replacement** → `{{LOGO_URL}}` is replaced with actual logo URL
9. ✅ **TenantId from request** → Uses tenantId from BatchJobEmailRequest, not template's tenantId

## Logging Requirements

Add debug logging at these points:
- When checking template header image URL
- When falling back to tenant settings
- When tenant settings are found/not found
- When adding header image to email HTML
- When downloading footer HTML from S3
- When replacing {{LOGO_URL}} placeholder
- When caching footer HTML
- Cache hits/misses

## Example Email HTML Structure

The final email HTML should have this structure:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset='UTF-8'>
</head>
<body>
    <!-- Header Image (from template OR tenant settings) -->
    <div style='text-align: center; margin-bottom: 20px;'>
        <img src='[header_image_url]' alt='Header' style='max-width: 100%; height: auto;' />
    </div>

    <!-- Email Body -->
    <div>[template body HTML]</div>

    <!-- Footer (from template OR tenant settings) -->
    <div>[footer HTML with logo replaced]</div>
</body>
</html>
```

## Integration Points

### Where to Apply This Change

1. **Email Content Building Service/Class**: Find where you build email HTML from templates
2. **Batch Email Processor**: Ensure it passes `tenantId` from `BatchJobEmailRequest` to `buildEmailContent`
3. **Repository Access**: Ensure you have access to `TenantSettingsRepository` and `S3Service`

### Critical Notes

1. **Always use tenantId from BatchJobEmailRequest**: Don't rely on template's tenantId for fallback lookups
2. **Cache key must include logoImageUrl**: This ensures cache invalidation when tenant logo changes
3. **Graceful degradation**: Never throw exceptions - return empty strings if tenant settings are missing
4. **S3 download errors**: Log warnings but don't fail the entire email batch
5. **Template footer takes precedence**: Only use tenant footer if template has no footer HTML or footer image

## Reference Implementation

The reference implementation is in:
- Backend Project: `src/main/java/com/nextjstemplate/service/impl/PromotionEmailServiceImpl.java`
- Method: `buildEmailContent(PromotionEmailTemplate template, String subjectOverride, String bodyHtmlOverride, String tenantIdForFallback)`
- Helper Methods: `getTenantEmailHeaderImageUrl(String tenantId)` and `getTenantEmailFooterHtml(String tenantId)`

## Questions to Clarify

If you need clarification on:
1. How to access TenantSettingsRepository in your batch job service
2. How to access S3Service for downloading footer HTML
3. The exact structure of your email building code
4. How tenantId flows through your batch processing pipeline

Please refer to the backend implementation or ask for clarification.

## Success Criteria

The implementation is successful when:
- ✅ Bulk emails include tenant header images when templates don't have them
- ✅ Bulk emails include tenant footers with logos when templates don't have footers
- ✅ No errors occur when tenant settings are missing
- ✅ Caching reduces S3 download calls
- ✅ Logo replacement works correctly in footer HTML
- ✅ All existing functionality continues to work (backward compatible)

