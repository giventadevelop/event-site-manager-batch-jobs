-- Create batch_job_execution_log table for tracking batch job executions
-- This table is separate from Spring Batch's internal tables (BATCH_*)
-- Note: Renamed from batch_job_execution to batch_job_execution_log to avoid confusion with Spring Batch's BATCH_JOB_EXECUTION
-- Run this script before starting the application if using ddl-auto: validate

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

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_tenant
    ON public.batch_job_execution_log(tenant_id, started_at DESC);

