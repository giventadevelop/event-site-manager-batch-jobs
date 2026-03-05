-- Add homepage_cache_version to tenant_settings for cache invalidation
-- Run on the main database when deploying. Safe to re-run (IF NOT EXISTS).

ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS homepage_cache_version bigint DEFAULT 0 NOT NULL;
