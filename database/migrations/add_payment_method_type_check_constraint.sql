-- Migration: Add CHECK constraint for payment_method_type in manual_payment_summary_report
-- Purpose: Ensure payment_method_type values match the source enum values from manual_payment_request
-- Date: 2026-01-19

-- This migration adds/updates the CHECK constraint for payment_method_type
-- Based on actual enum values: ZELLE_MANUAL, CASH, VENMO_MANUAL

BEGIN;

-- Drop existing constraint if it exists (with different values)
ALTER TABLE manual_payment_summary_report
    DROP CONSTRAINT IF EXISTS chk_payment_method_type,
    DROP CONSTRAINT IF EXISTS check_manual_payment_summary_payment_method_type;

-- Add CHECK constraint with actual enum values
ALTER TABLE manual_payment_summary_report
    ADD CONSTRAINT check_manual_payment_summary_payment_method_type 
    CHECK (payment_method_type IN ('ZELLE_MANUAL', 'CASH', 'VENMO_MANUAL'));

COMMIT;

-- Verification query (run after migration):
-- SELECT constraint_name, check_clause 
-- FROM information_schema.check_constraints 
-- WHERE constraint_name = 'check_manual_payment_summary_payment_method_type';
