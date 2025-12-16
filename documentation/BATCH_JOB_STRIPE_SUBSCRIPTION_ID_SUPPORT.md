# Batch Job Project: Add stripeSubscriptionId Support

## Context

The backend project (`malayalees-us-site-boot`) has been refactored to call the batch job microservice (`event-site-manager-batch-jobs`) via REST API instead of using AWS Batch SDK directly.

The backend's `BatchJobRequest` includes an optional `stripeSubscriptionId` parameter that allows processing a single subscription renewal instead of all subscriptions. However, the batch job project's `BatchJobRequest` DTO and service implementation do not yet support this parameter.

## Task

Add support for `stripeSubscriptionId` parameter in the batch job project to allow processing a single subscription renewal.

## Location

**Batch Job Project**: `E:\project_workspace\event-site-manager-batch-jobs`

## Files to Modify

### 1. Update `BatchJobRequest` DTO

**File**: `src/main/java/com/eventmanager/batch/dto/BatchJobRequest.java`

Add the `stripeSubscriptionId` field:

```java
@Data
public class BatchJobRequest {
    private String tenantId;
    private Integer batchSize;
    private Integer maxSubscriptions;
    private Integer maxEmails;
    private Long templateId;
    private List<String> recipientEmails;
    private Long userId;
    private String recipientType;
    private String stripeSubscriptionId; // NEW: Optional - if provided, process only this subscription
}
```

### 2. Update `BatchJobOrchestrationService`

**File**: `src/main/java/com/eventmanager/batch/service/BatchJobOrchestrationService.java`

Modify the `runSubscriptionRenewalJob` method to accept and use `stripeSubscriptionId`:

```java
public BatchJobResponse runSubscriptionRenewalJob(
    String tenantId,
    Integer batchSize,
    Integer maxSubscriptions,
    String stripeSubscriptionId  // NEW parameter
) {
    log.info("Starting subscription renewal batch job for tenant: {}, stripeSubscriptionId: {}",
        tenantId, stripeSubscriptionId);

    // ... existing code ...

    // Configure reader and processor for tenant and stripeSubscriptionId
    if (tenantId != null && !tenantId.isEmpty()) {
        subscriptionRenewalReader.setTenantId(tenantId);
        subscriptionRenewalProcessor.setTenantId(tenantId);
    }

    // NEW: Configure reader and processor for stripeSubscriptionId if provided
    if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
        subscriptionRenewalReader.setStripeSubscriptionId(stripeSubscriptionId);
        subscriptionRenewalProcessor.setStripeSubscriptionId(stripeSubscriptionId);
    }

    // ... rest of existing code ...
}
```

### 3. Update `BatchJobController`

**File**: `src/main/java/com/eventmanager/batch/controller/BatchJobController.java`

Pass `stripeSubscriptionId` to the service:

```java
@PostMapping("/subscription-renewal")
public ResponseEntity<BatchJobResponse> triggerSubscriptionRenewal(@RequestBody BatchJobRequest request) {
    try {
        log.info("Received request to trigger subscription renewal job for tenant: {}, stripeSubscriptionId: {}",
            request.getTenantId(), request.getStripeSubscriptionId());

        BatchJobResponse response = batchJobOrchestrationService.runSubscriptionRenewalJob(
            request.getTenantId(),
            request.getBatchSize(),
            request.getMaxSubscriptions(),
            request.getStripeSubscriptionId()  // NEW: Pass stripeSubscriptionId
        );

        return ResponseEntity.ok(response);
    } catch (Exception e) {
        // ... existing error handling ...
    }
}
```

### 4. Update `SubscriptionRenewalReader`

**File**: `src/main/java/com/eventmanager/batch/job/subscription/reader/SubscriptionRenewalReader.java`

Add support for filtering by `stripeSubscriptionId`:

```java
private String stripeSubscriptionId;

public void setStripeSubscriptionId(String stripeSubscriptionId) {
    this.stripeSubscriptionId = stripeSubscriptionId;
}

// In the read() method, modify the query logic:
@Override
public MembershipSubscription read() throws Exception {
    // ... existing code ...

    if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
        // If stripeSubscriptionId is provided, process only that subscription
        Optional<MembershipSubscription> subscription = repository
            .findByStripeSubscriptionIdAndTenantId(stripeSubscriptionId, tenantId);

        if (subscription.isPresent()) {
            // Check if it's approaching renewal
            LocalDate renewalDate = subscription.get().getRenewalDate();
            if (renewalDate != null && !renewalDate.isAfter(checkDate)) {
                return subscription.get();
            }
        }
        return null; // No subscription found or not approaching renewal
    }

    // ... existing logic for processing all subscriptions ...
}
```

### 5. Update `MembershipSubscriptionRepository`

**File**: `src/main/java/com/eventmanager/batch/repository/MembershipSubscriptionRepository.java`

Ensure the repository has the method to find by `stripeSubscriptionId` and `tenantId`:

```java
@Query("SELECT s FROM MembershipSubscription s WHERE s.stripeSubscriptionId = :stripeSubscriptionId AND s.tenantId = :tenantId")
Optional<MembershipSubscription> findByStripeSubscriptionIdAndTenantId(
    @Param("stripeSubscriptionId") String stripeSubscriptionId,
    @Param("tenantId") String tenantId
);
```

## Behavior

### When `stripeSubscriptionId` is provided:
- Process only the subscription with the matching `stripeSubscriptionId` and `tenantId`
- Skip the batch processing logic (no need to query all subscriptions)
- Still check if the subscription is approaching renewal date
- Return early if the subscription is not found or not approaching renewal

### When `stripeSubscriptionId` is NOT provided:
- Process all subscriptions approaching renewal (existing behavior)
- Use `batchSize` and `maxSubscriptions` parameters as before

## Testing

### Test Case 1: Single Subscription Renewal
```bash
curl -X POST http://localhost:8081/batch-jobs/api/batch-jobs/subscription-renewal \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant_demo_002",
    "stripeSubscriptionId": "sub_1234567890"
  }'
```

**Expected**: Only the subscription with `stripeSubscriptionId = "sub_1234567890"` is processed.

### Test Case 2: All Subscriptions (No stripeSubscriptionId)
```bash
curl -X POST http://localhost:8081/batch-jobs/api/batch-jobs/subscription-renewal \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant_demo_002",
    "batchSize": 100,
    "maxSubscriptions": 10000
  }'
```

**Expected**: All subscriptions approaching renewal for the tenant are processed (existing behavior).

### Test Case 3: Invalid stripeSubscriptionId
```bash
curl -X POST http://localhost:8081/batch-jobs/api/batch-jobs/subscription-renewal \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant_demo_002",
    "stripeSubscriptionId": "sub_nonexistent"
  }'
```

**Expected**: Job completes successfully but processes 0 subscriptions (subscription not found).

## Related Files

- Backend project: `E:\project_workspace\malayalees-us-site-boot`
  - `src/main/java/com/nextjstemplate/service/BatchJobService.java` - Calls this endpoint
  - `src/main/java/com/nextjstemplate/service/dto/BatchJobRequest.java` - Includes `stripeSubscriptionId`
  - `src/main/java/com/nextjstemplate/web/rest/BatchJobResource.java` - Endpoint that triggers this

## Notes

1. The `stripeSubscriptionId` parameter is optional - if not provided, the existing batch processing behavior should continue unchanged.
2. When `stripeSubscriptionId` is provided, `batchSize` and `maxSubscriptions` can be ignored (only one subscription will be processed).
3. Ensure proper logging to track when single subscription renewal is triggered vs batch renewal.
4. The subscription must still be approaching renewal date to be processed (don't skip the renewal date check).

