# Backend API Integration Prompt for Batch Email Service

## Context
You are working on a Spring Boot backend application that needs to integrate with a separate batch jobs microservice to trigger batch email sending processes. The batch jobs service is running locally on port 8081 with context path `/batch-jobs`. Your backend application is also running locally in development mode.

## Task
Implement a REST client service in your Spring Boot backend application that can call the batch jobs API to trigger batch email sending. The integration should be production-ready with proper error handling, logging, and configuration management.

## API Details

### Base URL
- **Development**: `http://localhost:8081/batch-jobs`
- **Context Path**: `/batch-jobs` (already included in base URL)
- **Port**: `8081` (to avoid conflict with backend on 8080)

### Endpoint
**POST** `/api/batch-jobs/email`

### Request Headers
```
Content-Type: application/json
```

### Request Body (JSON)
```json
{
  "tenantId": "string (optional)",
  "batchSize": 50,
  "maxEmails": 10000
}
```

**Request Fields:**
- `tenantId` (String, optional): The tenant ID to process emails for. If not provided or null, processes for all tenants.
- `batchSize` (Integer, optional): Number of emails to process per batch. Default: 50
- `maxEmails` (Integer, optional): Maximum number of emails to process in this job run. Default: 10000

### Response Body (JSON)
```json
{
  "success": true,
  "message": "Email batch job started successfully",
  "jobExecutionId": 12345,
  "processedCount": 0,
  "successCount": 0,
  "failedCount": 0,
  "durationMs": null
}
```

**Response Fields:**
- `success` (Boolean): Whether the job was triggered successfully
- `message` (String): Human-readable status message
- `jobExecutionId` (Long, optional): Unique identifier for this job execution
- `processedCount` (Long, optional): Number of items processed (may be 0 initially)
- `successCount` (Long, optional): Number of successful operations
- `failedCount` (Long, optional): Number of failed operations
- `durationMs` (Long, optional): Job duration in milliseconds (null if still running)

### Error Response
If the request fails, you may receive:
- **HTTP 500**: Internal server error
- **Response body**:
```json
{
  "success": false,
  "message": "Failed to trigger job: [error details]"
}
```

## Implementation Requirements

### 1. Configuration
Create a configuration class or properties file entry for the batch jobs service URL:
```properties
batch-jobs.service.url=http://localhost:8081/batch-jobs
batch-jobs.service.timeout=30000
batch-jobs.service.enabled=true
```

### 2. DTOs
Create request and response DTOs matching the API contract:

**BatchJobEmailRequest.java:**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobEmailRequest {
    private String tenantId;
    private Integer batchSize;
    private Integer maxEmails;
}
```

**BatchJobEmailResponse.java:**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobEmailResponse {
    private Boolean success;
    private String message;
    private Long jobExecutionId;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
    private Long durationMs;
}
```

### 3. Service Interface
Create a service interface:
```java
public interface BatchJobEmailService {
    BatchJobEmailResponse triggerEmailBatch(String tenantId, Integer batchSize, Integer maxEmails);
    BatchJobEmailResponse triggerEmailBatch(String tenantId);
    BatchJobEmailResponse triggerEmailBatch();
}
```

### 4. Service Implementation
Implement the service using Spring's `RestTemplate` or `WebClient`:

**Using RestTemplate (Spring Boot 2.x):**
- Inject `RestTemplate` bean
- Use `postForObject()` or `exchange()` method
- Handle `HttpClientErrorException` and `HttpServerErrorException`
- Include proper logging

**Using WebClient (Spring Boot 2.5+, recommended):**
- Use reactive `WebClient` for better performance
- Configure timeout settings
- Handle errors with `onErrorResume()` or `onErrorMap()`
- Include proper logging

### 5. Error Handling
- Handle network timeouts gracefully
- Handle HTTP 4xx/5xx errors
- Log errors with appropriate log levels
- Return meaningful error messages to callers
- Consider implementing retry logic for transient failures

### 6. Logging
Include comprehensive logging:
- Log when batch job is triggered (INFO level)
- Log request details (tenantId, batchSize, maxEmails)
- Log response details (success, jobExecutionId)
- Log errors with full exception stack traces (ERROR level)

### 7. Health Check Integration
Optionally implement a health check that verifies the batch jobs service is reachable:
- Call `GET /api/batch-jobs/health`
- Expected response: `"Batch Jobs Service is running"`
- Use this in your application's health check endpoint

## Example Usage

### From a Controller
```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final BatchJobEmailService batchJobEmailService;

    @PostMapping("/trigger-batch-emails")
    public ResponseEntity<BatchJobEmailResponse> triggerBatchEmails(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Integer maxEmails) {

        BatchJobEmailResponse response = batchJobEmailService.triggerEmailBatch(
            tenantId, batchSize, maxEmails
        );

        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
        }
    }
}
```

### From a Scheduled Task
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledEmailBatchTrigger {

    private final BatchJobEmailService batchJobEmailService;

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void triggerDailyEmailBatch() {
        log.info("Triggering daily email batch job");
        BatchJobEmailResponse response = batchJobEmailService.triggerEmailBatch();

        if (response.getSuccess()) {
            log.info("Email batch job triggered successfully. Job ID: {}",
                response.getJobExecutionId());
        } else {
            log.error("Failed to trigger email batch job: {}",
                response.getMessage());
        }
    }
}
```

## Testing Considerations

1. **Unit Tests**: Mock the HTTP client and test error scenarios
2. **Integration Tests**: Test against a running batch jobs service (if available)
3. **Error Scenarios**: Test timeout, connection refused, HTTP errors
4. **Null Handling**: Test with null tenantId, batchSize, maxEmails

## Additional Notes

- The batch jobs service runs asynchronously - the API returns immediately after triggering the job
- The `jobExecutionId` can be used to track job status (if you implement status checking)
- The service shares the same database, so ensure database connectivity is configured
- For production, you'll need to configure the batch jobs service URL via environment variables
- Consider adding circuit breaker pattern (Resilience4j) for production resilience

## Configuration Example (application.yml)

```yaml
batch-jobs:
  service:
    url: ${BATCH_JOBS_SERVICE_URL:http://localhost:8081/batch-jobs}
    timeout: ${BATCH_JOBS_SERVICE_TIMEOUT:30000}
    enabled: ${BATCH_JOBS_SERVICE_ENABLED:true}
```

## Environment Variables for Production

```env
BATCH_JOBS_SERVICE_URL=http://batch-jobs-service:8081/batch-jobs
BATCH_JOBS_SERVICE_TIMEOUT=30000
BATCH_JOBS_SERVICE_ENABLED=true
```

---

**Important**: Ensure your backend application can reach the batch jobs service at `http://localhost:8081` when running locally. Both services should be running simultaneously for the integration to work.

