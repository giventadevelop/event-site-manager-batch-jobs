-- Migration: Convert enum columns to VARCHAR in manual_payment_summary_report
-- Purpose: Align with batch jobs project pattern of using VARCHAR instead of PostgreSQL enum types
-- Date: 2026-01-19
-- Reference: .cursor/rules/postgresql-enum-mapping.mdc

-- This migration converts enum columns to VARCHAR in manual_payment_summary_report table
-- to match the batch jobs project pattern (see .cursor/rules/postgresql-enum-mapping.mdc)

-- IMPORTANT: Before running this migration, verify the enum values by running:
-- SELECT DISTINCT payment_method_type::text FROM manual_payment_request;
-- SELECT DISTINCT status::text FROM manual_payment_request;

BEGIN;

-- Step 1: Check if columns are enum types (informational)
DO $$
DECLARE
    payment_method_type_type text;
    status_type text;
BEGIN
    SELECT data_type INTO payment_method_type_type
    FROM information_schema.columns
    WHERE table_name = 'manual_payment_summary_report' 
      AND column_name = 'payment_method_type';
    
    SELECT data_type INTO status_type
    FROM information_schema.columns
    WHERE table_name = 'manual_payment_summary_report' 
      AND column_name = 'status';
    
    RAISE NOTICE 'Current payment_method_type type: %', payment_method_type_type;
    RAISE NOTICE 'Current status type: %', status_type;
END $$;

-- Step 2: Add temporary VARCHAR columns
ALTER TABLE manual_payment_summary_report
    ADD COLUMN IF NOT EXISTS payment_method_type_varchar VARCHAR(50),
    ADD COLUMN IF NOT EXISTS status_varchar VARCHAR(50);

-- Step 3: Copy data from enum columns to VARCHAR columns (cast enum to text)
UPDATE manual_payment_summary_report
SET payment_method_type_varchar = payment_method_type::text,
    status_varchar = status::text
WHERE payment_method_type_varchar IS NULL OR status_varchar IS NULL;

-- Step 4: Drop the old enum columns (CASCADE will drop dependent objects like indexes)
ALTER TABLE manual_payment_summary_report
    DROP COLUMN IF EXISTS payment_method_type CASCADE,
    DROP COLUMN IF EXISTS status CASCADE;

-- Step 5: Rename VARCHAR columns to original names
ALTER TABLE manual_payment_summary_report
    RENAME COLUMN payment_method_type_varchar TO payment_method_type;

ALTER TABLE manual_payment_summary_report
    RENAME COLUMN status_varchar TO status;

-- Step 6: Add NOT NULL constraints
ALTER TABLE manual_payment_summary_report
    ALTER COLUMN payment_method_type SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

-- Step 7: Add CHECK constraints for data validation
-- These values should match the enum values from manual_payment_request table
-- Common values (adjust based on actual enum definitions):
-- payment_method_type: ZELLE, VENMO, CASH_APP, OTHER (verify from source table)
-- status: PENDING, RECEIVED, CANCELLED, EXPIRED (verify from source table)

-- Drop existing constraints if they exist
ALTER TABLE manual_payment_summary_report
    DROP CONSTRAINT IF EXISTS chk_payment_method_type,
    DROP CONSTRAINT IF EXISTS chk_status;

-- Add CHECK constraints
-- NOTE: Update these IN clauses based on actual enum values from your database
ALTER TABLE manual_payment_summary_report
    ADD CONSTRAINT chk_payment_method_type 
    CHECK (payment_method_type IN ('ZELLE', 'VENMO', 'CASH_APP', 'OTHER', 'CHECK', 'WIRE_TRANSFER'));

ALTER TABLE manual_payment_summary_report
    ADD CONSTRAINT chk_status 
    CHECK (status IN ('PENDING', 'RECEIVED', 'CANCELLED', 'EXPIRED', 'REJECTED'));

-- Step 8: Recreate indexes (if they were dropped by CASCADE)
-- Common indexes that might have existed:
CREATE INDEX IF NOT EXISTS idx_manual_payment_summary_report_payment_method 
    ON manual_payment_summary_report(payment_method_type);

CREATE INDEX IF NOT EXISTS idx_manual_payment_summary_report_status 
    ON manual_payment_summary_report(status);

CREATE INDEX IF NOT EXISTS idx_manual_payment_summary_report_tenant_event 
    ON manual_payment_summary_report(tenant_id, event_id, snapshot_date);

COMMIT;

-- Verification queries (run after migration):
-- 1. Verify column types:
--    SELECT column_name, data_type, character_maximum_length, is_nullable
--    FROM information_schema.columns
--    WHERE table_name = 'manual_payment_summary_report' 
--      AND column_name IN ('payment_method_type', 'status');
--
-- 2. Verify data integrity:
--    SELECT payment_method_type, status, COUNT(*) 
--    FROM manual_payment_summary_report 
--    GROUP BY payment_method_type, status;
--
-- 3. Verify constraints:
--    SELECT constraint_name, constraint_type 
--    FROM information_schema.table_constraints 
--    WHERE table_name = 'manual_payment_summary_report' 
--      AND constraint_name LIKE 'chk_%';
