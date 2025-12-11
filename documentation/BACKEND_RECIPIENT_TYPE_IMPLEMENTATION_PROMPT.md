# Backend Implementation Prompt: Add RecipientType Parameter for Email Batch Jobs

## Context
The batch jobs service now supports explicit differentiation between sending emails to **event attendees** vs **subscribed members**. You need to update your backend implementation to pass the `recipientType` parameter when calling the batch jobs API.

## Current Situation
- **Backend Endpoints**:
  - `/send-bulk` - Should send to event attendees
  - `/send-to-subscribed` - Should send to subscribed members
- **Batch Jobs API**: Single endpoint `/api/batch-jobs/email` that handles both cases
- **Issue**: Currently, the batch job infers recipient type from `template.eventId`, which may not always be accurate

## Required Changes

### 1. Update BatchJobRequest DTO (if you have one)

Add the `recipientType` field to your request DTO:

```java
public class BatchJobRequest {
    private String tenantId;
    private Integer batchSize;
    private Integer maxEmails;
    private Long templateId;
    private List<String> recipientEmails; // Optional
    private Long userId;
    private String recipientType; // NEW: "EVENT_ATTENDEES" or "SUBSCRIBED_MEMBERS"
}
```

### 2. Update Service Implementation

In your service that calls the batch jobs API (e.g., `BatchJobEmailServiceImpl` or `PromotionEmailServiceImpl`), update the method calls to include `recipientType`.

#### For `/send-bulk` Endpoint (Event Attendees)

When calling the batch job API from your `sendBulkEmail()` method, set `recipientType = "EVENT_ATTENDEES"`:

```java
@Override
public Map<String, Object> sendBulkEmail(Long templateId, List<String> recipientEmails, String tenantId, Long userId) {
    // ... existing code ...

    // Prepare batch job request
    BatchJobRequest batchRequest = new BatchJobRequest();
    batchRequest.setTenantId(tenantId);
    batchRequest.setTemplateId(templateId);
    batchRequest.setRecipientEmails(recipientEmails); // Optional: if null, batch job will fetch from EventAttendee
    batchRequest.setUserId(userId);
    batchRequest.setBatchSize(50); // SES recommended batch size
    batchRequest.setMaxEmails(10000);
    batchRequest.setRecipientType("EVENT_ATTENDEES"); // ← ADD THIS

    // Call batch jobs API
    BatchJobEmailResponse response = batchJobEmailService.triggerEmailBatch(batchRequest);

    // ... rest of your code ...
}
```

#### For `/send-to-subscribed` Endpoint (Subscribed Members)

When calling the batch job API from your `sendBulkEmailToSubscribedMembers()` method, set `recipientType = "SUBSCRIBED_MEMBERS"`:

```java
@Override
public Map<String, Object> sendBulkEmailToSubscribedMembers(Long templateId, String tenantId, Long userId) {
    // ... existing code ...

    // Prepare batch job request
    BatchJobRequest batchRequest = new BatchJobRequest();
    batchRequest.setTenantId(tenantId);
    batchRequest.setTemplateId(templateId);
    batchRequest.setRecipientEmails(null); // Will be fetched from UserProfile by batch job
    batchRequest.setUserId(userId);
    batchRequest.setBatchSize(50);
    batchRequest.setMaxEmails(10000);
    batchRequest.setRecipientType("SUBSCRIBED_MEMBERS"); // ← ADD THIS

    // Call batch jobs API
    BatchJobEmailResponse response = batchJobEmailService.triggerEmailBatch(batchRequest);

    // ... rest of your code ...
}
```

### 3. Update HTTP Client/Service

In your HTTP client service that makes the actual API call to the batch jobs service, ensure the `recipientType` field is included in the request body:

```java
@Service
public class BatchJobEmailServiceImpl {

    @Value("${batch.jobs.service.url:http://localhost:8081/batch-jobs}")
    private String batchJobsServiceUrl;

    public BatchJobEmailResponse triggerEmailBatch(BatchJobRequest request) {
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tenantId", request.getTenantId());
        requestBody.put("templateId", request.getTemplateId());
        requestBody.put("batchSize", request.getBatchSize());
        requestBody.put("maxEmails", request.getMaxEmails());
        requestBody.put("userId", request.getUserId());

        if (request.getRecipientEmails() != null && !request.getRecipientEmails().isEmpty()) {
            requestBody.put("recipientEmails", request.getRecipientEmails());
        }

        // ADD THIS LINE:
        if (request.getRecipientType() != null && !request.getRecipientType().isEmpty()) {
            requestBody.put("recipientType", request.getRecipientType());
        }

        // Make HTTP POST request to batch jobs service
        // POST {batchJobsServiceUrl}/api/batch-jobs/email
        // ... your HTTP client code ...
    }
}
```

## Request Body Format

### Complete Request Body Example

```json
{
  "tenantId": "tenant_demo_002",
  "templateId": 4102,
  "batchSize": 50,
  "maxEmails": 10000,
  "userId": 123,
  "recipientType": "EVENT_ATTENDEES",  // or "SUBSCRIBED_MEMBERS"
  "recipientEmails": null  // Optional: if provided, will use these; if null, batch job fetches from database
}
```

### Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tenantId` | String | Yes | Tenant identifier |
| `templateId` | Long | Yes | Email template ID |
| `batchSize` | Integer | No | Emails per batch (default: 50) |
| `maxEmails` | Integer | No | Maximum emails to process (default: 10000) |
| `userId` | Long | Yes | User ID who triggered the job (for logging) |
| `recipientType` | String | **NEW** | `"EVENT_ATTENDEES"` or `"SUBSCRIBED_MEMBERS"` |
| `recipientEmails` | List<String> | No | Explicit email list (if null, batch job fetches based on `recipientType`) |

## Recipient Type Values

### `"EVENT_ATTENDEES"`
- **Source**: `EventAttendee` table
- **Filter**: `registrationStatus = 'CONFIRMED'`
- **Scope**: Specific event (from `template.eventId`)
- **Use Case**: Sending emails to people who registered for a specific event

### `"SUBSCRIBED_MEMBERS"`
- **Source**: `UserProfile` table
- **Filter**: `isEmailSubscribed = true`
- **Scope**: All subscribed members for the tenant (not event-specific)
- **Use Case**: Sending promotional emails to all subscribed members of the organization

## Backward Compatibility

- **If `recipientType` is NOT provided**: Batch job will infer from `template.eventId`:
  - If `template.eventId != null` → Treated as `EVENT_ATTENDEES`
  - If `template.eventId == null` → Treated as `SUBSCRIBED_MEMBERS`
- **However, explicitly setting `recipientType` is recommended** for clarity and accuracy

## Implementation Checklist

- [ ] Add `recipientType` field to `BatchJobRequest` DTO
- [ ] Update `sendBulkEmail()` method to set `recipientType = "EVENT_ATTENDEES"`
- [ ] Update `sendBulkEmailToSubscribedMembers()` method to set `recipientType = "SUBSCRIBED_MEMBERS"`
- [ ] Update HTTP client to include `recipientType` in request body
- [ ] Test both endpoints to ensure correct recipient lists are used
- [ ] Update any API documentation or Swagger specs

## Testing

### Test Case 1: Send to Event Attendees
```java
// Call /send-bulk endpoint
// Verify recipientType = "EVENT_ATTENDEES" is sent
// Verify batch job receives correct recipient list from EventAttendee table
```

### Test Case 2: Send to Subscribed Members
```java
// Call /send-to-subscribed endpoint
// Verify recipientType = "SUBSCRIBED_MEMBERS" is sent
// Verify batch job receives correct recipient list from UserProfile table
```

## API Endpoint Reference

**Batch Jobs Service Endpoint:**
- **URL**: `POST {batchJobsServiceUrl}/api/batch-jobs/email`
- **Base URL**: `http://localhost:8081/batch-jobs` (development)
- **Content-Type**: `application/json`

## Questions or Issues?

If you encounter any issues or need clarification:
1. Check the batch jobs service logs for error messages
2. Verify the `recipientType` value is exactly `"EVENT_ATTENDEES"` or `"SUBSCRIBED_MEMBERS"` (case-insensitive)
3. Ensure `templateId` and `tenantId` are valid
4. Verify the template exists and belongs to the specified tenant

## Summary

**Key Change**: Add `recipientType` parameter to your batch job API calls:
- `/send-bulk` → `recipientType: "EVENT_ATTENDEES"`
- `/send-to-subscribed` → `recipientType: "SUBSCRIBED_MEMBERS"`

This ensures the batch job knows exactly which recipient list to use, making the behavior explicit and reliable.

