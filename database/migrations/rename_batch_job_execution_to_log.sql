-- Migration script to rename batch_job_execution to batch_job_execution_log
-- This avoids confusion with Spring Batch's BATCH_JOB_EXECUTION table
-- Run this script if you have an existing batch_job_execution table

-- Rename the table if it exists
ALTER TABLE IF EXISTS public.batch_job_execution
    RENAME TO batch_job_execution_log;

-- Drop old indexes if they exist and create new ones with updated names
DROP INDEX IF EXISTS public.idx_batch_job_execution_job_name;
DROP INDEX IF EXISTS public.idx_batch_job_execution_status;
DROP INDEX IF EXISTS public.idx_batch_job_execution_tenant;

-- Create indexes with new names
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_job_name
    ON public.batch_job_execution_log(job_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_status
    ON public.batch_job_execution_log(status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_tenant
    ON public.batch_job_execution_log(tenant_id, started_at DESC);

