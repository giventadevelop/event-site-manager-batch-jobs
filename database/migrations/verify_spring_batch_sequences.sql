-- Verification script to check Spring Batch sequence names
-- This script verifies the sequences exist and shows their names

-- Check Spring Batch sequences (created explicitly with names Spring Batch expects)
SELECT
    schemaname,
    sequencename,
    last_value
FROM pg_sequences
WHERE sequencename IN ('batch_job_seq', 'batch_job_execution_seq', 'batch_step_execution_seq')
ORDER BY sequencename;

-- Expected sequences (created explicitly to match Spring Batch's expectations):
-- batch_job_seq (for BATCH_JOB_INSTANCE.JOB_INSTANCE_ID)
-- batch_job_execution_seq (for BATCH_JOB_EXECUTION.JOB_EXECUTION_ID)
-- batch_step_execution_seq (for BATCH_STEP_EXECUTION.STEP_EXECUTION_ID)

-- Also check for the shared sequence_generator used by other application tables
SELECT
    schemaname,
    sequencename,
    last_value
FROM pg_sequences
WHERE sequencename = 'sequence_generator';

