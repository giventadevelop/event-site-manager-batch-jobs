# Event Site Manager Batch Jobs Service

Spring Boot Spring Batch application for processing subscription renewals and batch emails. This service is designed to run on AWS ECS Fargate as scheduled tasks to offload batch processing from the main backend application.

## Features

- **Subscription Renewal Batch Job**: Processes subscriptions approaching renewal date and syncs with Stripe
- **Email Batch Processing**: Sends batch emails via AWS SES (to be implemented)
- **REST API**: Programmatic job triggering from the main backend
- **Scheduled Tasks**: Cron-based job scheduling
- **Multi-Tenant Support**: Processes subscriptions per tenant
- **Monitoring**: Actuator endpoints for health checks and metrics

## Architecture

This service extracts batch processing functionality from the main backend (`malayalees-us-site-boot`) to:
- Reduce load on the main application
- Enable independent scaling of batch jobs
- Provide better isolation and fault tolerance
- Support scheduled execution via AWS ECS Fargate

## Technology Stack

- **Spring Boot 3.1.5**: Application framework
- **Spring Batch 5.0.2**: Batch processing framework
- **PostgreSQL**: Database (shared with main backend)
- **Stripe Java SDK**: Subscription management
- **AWS SDK for SES**: Email sending
- **Resilience4j**: Circuit breaker for external services
- **Guava**: Rate limiting

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL database (shared with main backend)
- AWS Account (for ECS Fargate deployment)
- Stripe API keys (stored in database per tenant)

## Configuration

### Environment Variables

```bash
# Database
RDS_ENDPOINT=localhost
DB_NAME=event_site_manager_db
DB_USERNAME=event_site_app
DB_PASSWORD=your-database-password
DB_MAX_POOL_SIZE=10
DB_MIN_IDLE=2

# AWS
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-1
AWS_SES_FROM_EMAIL=noreply@example.com
AWS_SES_RATE_LIMIT_PER_SECOND=200

# Batch Job Configuration
SUBSCRIPTION_RENEWAL_ENABLED=true
SUBSCRIPTION_RENEWAL_CRON=0 0 */6 * * *
SUBSCRIPTION_RENEWAL_BATCH_SIZE=100
SUBSCRIPTION_RENEWAL_MAX_SUBSCRIPTIONS=10000
SUBSCRIPTION_RENEWAL_DAYS_BEFORE=7

EMAIL_BATCH_ENABLED=true
EMAIL_BATCH_CRON=0 0 2 * * *
EMAIL_BATCH_SIZE=50
EMAIL_BATCH_MAX_EMAILS=10000
```

### Application Properties

See `src/main/resources/application.yml` for detailed configuration options.

## Building

```bash
mvn clean package
```

## Running Locally

```bash
# Set environment variables
export RDS_ENDPOINT=localhost
export DB_NAME=event_site_manager_db
export DB_USERNAME=event_site_app
export DB_PASSWORD=your-database-password
export DB_MAX_POOL_SIZE=10
export DB_MIN_IDLE=2

# Run the application
mvn spring-boot:run
```

## API Endpoints

### Trigger Subscription Renewal Job

```bash
POST /batch-jobs/api/batch-jobs/subscription-renewal
Content-Type: application/json

{
  "tenantId": "tenant_001",
  "batchSize": 100,
  "maxSubscriptions": 10000
}
```

### Trigger Email Batch Job

```bash
POST /batch-jobs/api/batch-jobs/email
Content-Type: application/json

{
  "tenantId": "tenant_001",
  "batchSize": 50,
  "maxEmails": 10000
}
```

### Health Check

```bash
GET /batch-jobs/api/batch-jobs/health
GET /batch-jobs/actuator/health
```

## Scheduled Jobs

Jobs can be scheduled using Spring's `@Scheduled` annotation or via AWS EventBridge/ECS Scheduled Tasks.

### Default Schedule

- **Subscription Renewal**: Every 6 hours (`0 0 */6 * * *`)
- **Email Batch**: Daily at 2 AM (`0 0 2 * * *`)

## Deployment to AWS ECS Fargate

### 1. Build and Push Docker Image

```bash
# Build Docker image
docker build -t event-site-manager-batch-jobs .

# Tag for ECR
docker tag event-site-manager-batch-jobs:latest YOUR_ECR_REPOSITORY_URI:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin YOUR_ECR_REPOSITORY_URI
docker push YOUR_ECR_REPOSITORY_URI:latest
```

### 2. Create ECS Task Definition

Update `ecs-task-definition.json` with your AWS account details and create the task definition:

```bash
aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json
```

### 3. Create Scheduled Task (EventBridge Rule)

Update `ecs-scheduled-task.json` with your cluster and subnet details, then create the scheduled task:

```bash
aws events put-rule --name subscription-renewal-scheduled-task --schedule-expression "cron(0 */6 * * ? *)"
aws events put-targets --rule subscription-renewal-scheduled-task --targets file://ecs-scheduled-task.json
```

### 4. Required IAM Roles

- **ECS Task Execution Role**: Needs permissions to pull from ECR and access Secrets Manager
- **ECS Task Role**: Needs permissions to access RDS, SES, and other AWS services
- **EventBridge Role**: Needs permissions to run ECS tasks

## Database Schema

The service uses the same database as the main backend. Ensure the following tables exist:

- `membership_subscription`: Subscription records
- `payment_provider_config`: Stripe API keys per tenant
- `batch_job_execution`: Job execution tracking (created automatically by Spring Batch)

### Additional Columns for Batch Jobs

The following columns should be added to `membership_subscription`:

```sql
ALTER TABLE membership_subscription
ADD COLUMN IF NOT EXISTS last_reconciliation_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS last_stripe_sync_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS reconciliation_error TEXT;
```

## Integration with Main Backend

The main backend can trigger batch jobs programmatically:

```java
// Example: Trigger subscription renewal from main backend
RestTemplate restTemplate = new RestTemplate();
String batchJobsUrl = "http://batch-jobs-service:8081/batch-jobs/api/batch-jobs/subscription-renewal";

BatchJobRequest request = new BatchJobRequest();
request.setTenantId(tenantId);
request.setBatchSize(100);
request.setMaxSubscriptions(10000);

ResponseEntity<BatchJobResponse> response = restTemplate.postForEntity(
    batchJobsUrl, request, BatchJobResponse.class
);
```

## Monitoring

- **Health Endpoint**: `/batch-jobs/actuator/health`
- **Metrics**: `/batch-jobs/actuator/prometheus`
- **Job Execution History**: Query `batch_job_execution` table

## Development

### Project Structure

```
src/main/java/com/eventmanager/batch/
├── BatchJobsApplication.java          # Main application class
├── config/                             # Configuration classes
│   └── BatchConfig.java
├── domain/                             # JPA entities
│   ├── MembershipSubscription.java
│   ├── PaymentProviderConfig.java
│   └── BatchJobExecution.java
├── repository/                         # JPA repositories
│   ├── MembershipSubscriptionRepository.java
│   └── PaymentProviderConfigRepository.java
├── service/                            # Business logic services
│   ├── StripeService.java
│   ├── EmailService.java
│   └── BatchJobExecutionService.java
├── job/                               # Batch job configurations
│   └── subscription/
│       ├── SubscriptionRenewalJobConfig.java
│       ├── reader/
│       ├── processor/
│       └── writer/
├── controller/                        # REST API controllers
│   └── BatchJobController.java
├── scheduler/                         # Scheduled tasks
│   └── BatchJobScheduler.java
└── dto/                               # Data transfer objects
    ├── BatchJobRequest.java
    └── BatchJobResponse.java
```

## Troubleshooting

### Common Issues

1. **Database Connection**: Ensure the database URL, username, and password are correct
2. **Stripe API Keys**: Verify tenant-specific Stripe keys are stored in `payment_provider_config` table
3. **AWS Credentials**: Check AWS access keys and region configuration
4. **Job Not Running**: Check Spring Batch job repository tables are initialized

### Logs

Check application logs for detailed error messages:
- Local: Console output
- ECS: CloudWatch Logs (`/ecs/event-site-manager-batch-jobs`)

## License

[Your License Here]

## Support

For issues and questions, please contact the development team.

