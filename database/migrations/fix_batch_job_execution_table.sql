-- Fix batch_job_execution_log table structure
-- This script removes the incorrect job_execution_id column if it exists
-- and ensures the table matches the entity definition
-- Note: Table renamed from batch_job_execution to batch_job_execution_log to avoid confusion with Spring Batch's BATCH_JOB_EXECUTION

-- Drop the job_execution_id column if it exists (it shouldn't be there)
ALTER TABLE IF EXISTS public.batch_job_execution_log
    DROP COLUMN IF EXISTS job_execution_id;

-- If old table exists, rename it
ALTER TABLE IF EXISTS public.batch_job_execution
    RENAME TO batch_job_execution_log;

-- Ensure the table has the correct structure
-- If the table doesn't exist, create it
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

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_job_name
    ON public.batch_job_execution_log(job_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_status
    ON public.batch_job_execution_log(status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_tenant
    ON public.batch_job_execution_log(tenant_id, started_at DESC);

