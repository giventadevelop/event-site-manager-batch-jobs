# Database Setup Guide

## Quick Setup

The application needs certain database tables to be created. You have two options:

### Option 1: Automatic Table Creation (Recommended for Development)

The application is configured to automatically create/update tables when `ddl-auto: update` is set (default in `application.yml`).

**Just start the application** - tables will be created automatically.

### Option 2: Manual Table Creation (Recommended for Production)

If you want to use `ddl-auto: validate` (schema validation only), you need to create the tables manually first.

#### Step 1: Run Migration Scripts

```bash
# Connect to your database
psql -h <RDS_ENDPOINT> -U <DB_USERNAME> -d <DB_NAME>

# Run the migration script
\i database/migrations/add_batch_job_columns.sql

# Or run the individual table creation script
\i database/migrations/create_batch_job_execution_table.sql
```

#### Step 2: Update Configuration

Set `ddl-auto: validate` in `application-prod.yml` (already configured).

## Required Tables

### 1. Spring Batch Tables (Auto-created)

Spring Batch will automatically create these tables with prefix `BATCH_`:
- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_STEP_EXECUTION`
- `BATCH_STEP_EXECUTION_CONTEXT`
- `BATCH_JOB_EXECUTION_CONTEXT`

### 2. Custom Tables (Need to be created)

#### `batch_job_execution`
Custom table for tracking batch job executions with additional metadata.

**Create manually:**
```sql
CREATE TABLE IF NOT EXISTS batch_job_execution (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(255),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    processed_count BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    failed_count BIGINT DEFAULT 0,
    error_message TEXT,
    triggered_by VARCHAR(100),
    parameters_json JSONB
);
```

#### `membership_subscription` (from main backend)

The application reads from the existing `membership_subscription` table. Ensure these columns exist:

```sql
-- Add reconciliation tracking columns (if not already present)
ALTER TABLE membership_subscription
ADD COLUMN IF NOT EXISTS last_reconciliation_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS last_stripe_sync_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS reconciliation_error TEXT;
```

#### `payment_provider_config` (from main backend)

The application reads Stripe API keys from this table. Ensure it exists.

## Verification

After setup, verify tables exist:

```sql
-- Check Spring Batch tables
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name LIKE 'BATCH_%';

-- Check custom tables
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('batch_job_execution', 'membership_subscription', 'payment_provider_config');
```

## Troubleshooting

### Error: "missing table [batch_job_execution]"

**Solution 1:** Run the migration script:
```bash
psql -h <host> -U <user> -d <database> -f database/migrations/create_batch_job_execution_table.sql
```

**Solution 2:** Change `ddl-auto` to `update` in `application.yml` (temporary, for development)

### Error: "missing table [membership_subscription]"

This table should exist from your main backend application. If it doesn't, you need to run the main backend migrations first.

### Error: "missing table [payment_provider_config]"

This table should exist from your main backend application. Ensure your backend database migrations have been run.



