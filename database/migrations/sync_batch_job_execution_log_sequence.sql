-- Sync batch_job_execution_log_id_seq sequence
-- This sequence is created automatically by PostgreSQL when using BIGSERIAL
-- Run this script if you're getting duplicate key errors on batch_job_execution_log table

-- Method 1: Sync sequence to current max ID + 1 (RECOMMENDED)
DO $$
DECLARE
    max_id_value BIGINT;
    next_sequence_value BIGINT;
BEGIN
    -- Get the maximum ID from batch_job_execution_log table
    SELECT COALESCE(MAX(id), 0) INTO max_id_value
    FROM public.batch_job_execution_log;

    -- Set sequence to max ID + 1 (or 1 if table is empty)
    next_sequence_value := GREATEST(max_id_value + 1, 1);

    -- Check if sequence exists before trying to sync
    IF EXISTS (
        SELECT 1
        FROM pg_sequences
        WHERE schemaname = 'public'
          AND sequencename = 'batch_job_execution_log_id_seq'
    ) THEN
        -- Set the sequence value
        -- The 'true' parameter means nextval() will return the value we set
        PERFORM setval('public.batch_job_execution_log_id_seq', next_sequence_value - 1, true);

        RAISE NOTICE 'Sequence synchronized: Max ID = %, Next sequence value = %',
            max_id_value, next_sequence_value;
    ELSE
        RAISE WARNING 'Sequence batch_job_execution_log_id_seq does not exist. ' ||
            'Table may not use BIGSERIAL or sequence was not created.';
    END IF;
END $$;

-- Verify the synchronization
SELECT
    (SELECT last_value FROM public.batch_job_execution_log_id_seq) as sequence_last_value,
    (SELECT COALESCE(MAX(id), 0) FROM public.batch_job_execution_log) as max_id_in_table,
    CASE
        WHEN (SELECT last_value FROM public.batch_job_execution_log_id_seq) >= 
             (SELECT COALESCE(MAX(id), 0) FROM public.batch_job_execution_log)
        THEN 'OK: Sequence is synchronized'
        ELSE 'WARNING: Sequence may still be out of sync'
    END as sync_status;
