-- Migration: Add event_focus_group_id to event_media
-- Purpose: Align batch job schema with Focus Group – Event Media Integration (PRD: documentation/focus_groups/batch_job_prd.html)
-- The column is nullable for backward compatibility; no job logic changes required.
-- Safe to re-run (idempotent: ADD COLUMN IF NOT EXISTS, conditional FK, CREATE INDEX IF NOT EXISTS).

-- Add column
ALTER TABLE public.event_media
    ADD COLUMN IF NOT EXISTS event_focus_group_id bigint NULL;

-- Add FK only if it does not exist (avoids duplicate constraint on re-run)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.event_media'::regclass
          AND conname = 'fk_event_media_event_focus_group_id'
    ) THEN
        ALTER TABLE public.event_media
            ADD CONSTRAINT fk_event_media_event_focus_group_id
            FOREIGN KEY (event_focus_group_id)
            REFERENCES public.event_focus_groups(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Partial index for lookups by focus group
CREATE INDEX IF NOT EXISTS idx_event_media_event_focus_group_id
    ON public.event_media(event_focus_group_id)
    WHERE event_focus_group_id IS NOT NULL;
