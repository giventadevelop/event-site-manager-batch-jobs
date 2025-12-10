# Quick Start Guide

## Overview

This Spring Boot Spring Batch project handles subscription renewal and batch email processing for the Event Site Manager application. It runs independently from the main backend to reduce load and can be deployed to AWS ECS Fargate.

## Project Structure

```
event-site-manager-batch-jobs/
├── src/main/java/com/eventmanager/batch/
│   ├── BatchJobsApplication.java          # Main application entry point
│   ├── config/                             # Configuration classes
│   ├── domain/                             # JPA entities
│   ├── repository/                         # Data access layer
│   ├── service/                            # Business logic
│   ├── job/                                # Batch job definitions
│   │   └── subscription/
│   │       ├── SubscriptionRenewalJobConfig.java
│   │       ├── reader/                     # Data readers
│   │       ├── processor/                  # Data processors
│   │       └── writer/                     # Data writers
│   ├── controller/                         # REST API endpoints
│   ├── scheduler/                          # Scheduled tasks
│   └── dto/                                # Data transfer objects
├── src/main/resources/
│   ├── application.yml                     # Main configuration
│   ├── application-prod.yml               # Production config
│   └── application-dev.yml                # Development config
├── database/migrations/                    # SQL migration scripts
├── scripts/                                # Deployment scripts
├── Dockerfile                              # Container definition
├── ecs-task-definition.json               # ECS task definition
├── ecs-scheduled-task.json                # Scheduled task config
└── README.md                               # Full documentation
```

## Key Components

### 1. Subscription Renewal Batch Job

**Purpose**: Syncs subscription data with Stripe and updates local database records.

**How it works**:
1. Reader queries subscriptions approaching renewal date
2. Processor fetches latest data from Stripe API
3. Writer updates database records

**Configuration**:
- Batch size: 100 subscriptions per chunk
- Max subscriptions: 10,000 per run
- Days before renewal: 7 days
- Schedule: Every 6 hours (configurable)

### 2. Email Batch Job

**Purpose**: Sends batch emails via AWS SES (to be implemented).

**Configuration**:
- Batch size: 50 emails per batch
- Max emails: 10,000 per run
- Schedule: Daily at 2 AM (configurable)

### 3. REST API

**Endpoints**:
- `POST /batch-jobs/api/batch-jobs/subscription-renewal` - Trigger subscription renewal
- `POST /batch-jobs/api/batch-jobs/email` - Trigger email batch
- `GET /batch-jobs/api/batch-jobs/health` - Health check

## Setup Steps

### 1. Database Setup

Run the migration script to add required columns:

```bash
psql -d event_manager -f database/migrations/add_batch_job_columns.sql
```

### 2. Local Development

```bash
# Set environment variables
export RDS_ENDPOINT=localhost
export DB_NAME=event_site_manager_db
export DB_USERNAME=event_site_app
export DB_PASSWORD=event_site_app!
export DB_MAX_POOL_SIZE=10
export DB_MIN_IDLE=2
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=us-east-1

# Run the application
mvn spring-boot:run
```

### 3. Build and Test

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run application
java -jar target/event-site-manager-batch-jobs-0.0.1-SNAPSHOT.jar
```

### 4. Docker Build

```bash
# Build Docker image
docker build -t event-site-manager-batch-jobs:latest .

# Run container locally
docker run -p 8081:8081 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/event_manager \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=postgres \
  event-site-manager-batch-jobs:latest
```

### 5. Deploy to AWS ECS

```bash
# Using deployment script (Linux/Mac)
chmod +x scripts/deploy.sh
./scripts/deploy.sh

# Using deployment script (Windows PowerShell)
.\scripts\deploy.ps1

# Or manually:
# 1. Build and push to ECR
# 2. Update ecs-task-definition.json with your ECR URI
# 3. Register task definition
aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json
# 4. Create scheduled task
aws events put-rule --name subscription-renewal-scheduled-task --schedule-expression "cron(0 */6 * * ? *)"
aws events put-targets --rule subscription-renewal-scheduled-task --targets file://ecs-scheduled-task.json
```

## Integration with Main Backend

The main backend can trigger batch jobs programmatically:

### Java Example

```java
@Autowired
private RestTemplate restTemplate;

public void triggerSubscriptionRenewal(String tenantId) {
    String url = "http://batch-jobs-service:8081/batch-jobs/api/batch-jobs/subscription-renewal";

    BatchJobRequest request = new BatchJobRequest();
    request.setTenantId(tenantId);
    request.setBatchSize(100);
    request.setMaxSubscriptions(10000);

    ResponseEntity<BatchJobResponse> response = restTemplate.postForEntity(
        url, request, BatchJobResponse.class
    );

    if (response.getBody().getSuccess()) {
        log.info("Batch job started: {}", response.getBody().getJobExecutionId());
    }
}
```

### cURL Example

```bash
curl -X POST http://localhost:8081/batch-jobs/api/batch-jobs/subscription-renewal \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant_001",
    "batchSize": 100,
    "maxSubscriptions": 10000
  }'
```

## Monitoring

### Health Check

```bash
curl http://localhost:8081/batch-jobs/actuator/health
```

### Metrics

```bash
curl http://localhost:8081/batch-jobs/actuator/prometheus
```

### Job Execution History

Query the `batch_job_execution` table:

```sql
SELECT * FROM batch_job_execution
ORDER BY started_at DESC
LIMIT 10;
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `RDS_ENDPOINT` | PostgreSQL host/endpoint | `localhost` |
| `DB_NAME` | Database name | `event_site_manager_db` |
| `DB_USERNAME` | Database username | `event_site_app` |
| `DB_PASSWORD` | Database password | `event_site_app!` |
| `DB_MAX_POOL_SIZE` | Maximum connection pool size | `10` |
| `DB_MIN_IDLE` | Minimum idle connections | `2` |
| `AWS_ACCESS_KEY_ID` | AWS access key | - |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | - |
| `AWS_REGION` | AWS region | `us-east-1` |
| `AWS_SES_FROM_EMAIL` | Default sender email | `noreply@example.com` |
| `SUBSCRIPTION_RENEWAL_ENABLED` | Enable subscription renewal job | `true` |
| `SUBSCRIPTION_RENEWAL_CRON` | Cron expression for scheduling | `0 0 */6 * * *` |
| `SUBSCRIPTION_RENEWAL_BATCH_SIZE` | Batch size for processing | `100` |
| `SUBSCRIPTION_RENEWAL_MAX_SUBSCRIPTIONS` | Max subscriptions per run | `10000` |

### Application Properties

See `src/main/resources/application.yml` for all configuration options.

## Troubleshooting

### Job Not Running

1. Check Spring Batch tables are initialized:
   ```sql
   SELECT * FROM batch_job_instance;
   ```

2. Check application logs for errors

3. Verify database connection

4. Check Stripe API keys in `payment_provider_config` table

### Database Connection Issues

- Verify `RDS_ENDPOINT` is correct (hostname or IP)
- Verify `DB_NAME` matches your database name
- Check network connectivity
- Verify `DB_USERNAME` and `DB_PASSWORD` credentials

### Stripe API Errors

- Verify tenant-specific Stripe keys exist in `payment_provider_config` table
- Check Stripe API key format (starts with `sk_test_` or `sk_live_`)
- Verify Stripe account has necessary permissions

### AWS SES Errors

- Verify AWS credentials
- Check SES service limits
- Verify sender email is verified in SES

## Next Steps

1. **Implement Email Batch Job**: Complete the email batch processing functionality
2. **Add More Batch Jobs**: Implement other batch processing needs
3. **Enhanced Monitoring**: Add more detailed metrics and alerts
4. **Error Handling**: Improve error recovery and retry logic
5. **Testing**: Add comprehensive unit and integration tests

## Support

For issues or questions, refer to the main README.md or contact the development team.

