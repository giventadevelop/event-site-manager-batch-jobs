-- Migration script to add batch job tracking columns to membership_subscription table
-- Run this script on the main database before deploying the batch jobs service

-- Add reconciliation tracking columns
ALTER TABLE membership_subscription
ADD COLUMN IF NOT EXISTS last_reconciliation_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS last_stripe_sync_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS reconciliation_error TEXT;

-- Create index for renewal query optimization
CREATE INDEX IF NOT EXISTS idx_membership_subscription_renewal_check
ON membership_subscription(subscription_status, current_period_end, cancel_at_period_end)
WHERE subscription_status IN ('ACTIVE', 'TRIAL');

-- Create index for Stripe subscription lookup
CREATE INDEX IF NOT EXISTS idx_membership_subscription_stripe_id
ON membership_subscription(stripe_subscription_id)
WHERE stripe_subscription_id IS NOT NULL;

-- Create index for reconciliation
CREATE INDEX IF NOT EXISTS idx_membership_subscription_reconciliation
ON membership_subscription(reconciliation_status, last_reconciliation_at);

-- Create batch_job_execution_log table (custom application table, separate from Spring Batch's BATCH_JOB_EXECUTION)
-- Note: Renamed from batch_job_execution to batch_job_execution_log to avoid confusion
CREATE TABLE IF NOT EXISTS public.batch_job_execution_log (
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
    parameters_json TEXT
);

-- Create indexes for batch_job_execution_log
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_job_name
    ON public.batch_job_execution_log(job_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_status
    ON public.batch_job_execution_log(status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_tenant
ON batch_job_execution(tenant_id, started_at DESC);

-- Create reconciliation log table (optional, for detailed audit trail)
CREATE TABLE IF NOT EXISTS membership_subscription_reconciliation_log (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    reconciliation_type VARCHAR(50) NOT NULL, -- 'BATCH_RENEWAL', 'DAILY_RECONCILIATION', 'WEBHOOK'
    status VARCHAR(20) NOT NULL, -- 'SUCCESS', 'FAILED', 'SKIPPED'
    local_period_start DATE,
    local_period_end DATE,
    stripe_period_start DATE,
    stripe_period_end DATE,
    local_status VARCHAR(20),
    stripe_status VARCHAR(20),
    changes_json TEXT,
    error_message TEXT,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (subscription_id) REFERENCES membership_subscription(id)
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_log_subscription
ON membership_subscription_reconciliation_log(subscription_id, processed_at DESC);

CREATE INDEX IF NOT EXISTS idx_reconciliation_log_tenant
ON membership_subscription_reconciliation_log(tenant_id, processed_at DESC);




