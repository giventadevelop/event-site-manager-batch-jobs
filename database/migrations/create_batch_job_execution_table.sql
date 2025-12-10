-- Create batch_job_execution table for tracking batch job executions
-- This table is separate from Spring Batch's internal tables (BATCH_*)
-- Run this script before starting the application if using ddl-auto: validate

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
    parameters_json TEXT
);

-- Create indexes for batch_job_execution
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_job_name
ON batch_job_execution(job_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_status
ON batch_job_execution(status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_tenant
ON batch_job_execution(tenant_id, started_at DESC);

