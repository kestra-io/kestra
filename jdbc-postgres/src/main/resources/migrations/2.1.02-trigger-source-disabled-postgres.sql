-- Postgres side of the 2.1.02-trigger-source-disabled migration; see
-- 2.1.02-trigger-source-disabled-h2.sql for why the column exists.
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "source_disabled" BOOLEAN NOT NULL GENERATED ALWAYS AS (COALESCE(CAST(value ->> 'sourceDisabled' AS BOOLEAN), FALSE)) STORED;
