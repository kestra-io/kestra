-- 2.1.02: triggers.disabled now holds only the runtime disable (UI toggle, stopAfter, invalid
-- configuration), so the trigger-state search filter could no longer see a trigger disabled in its
-- flow definition. Mirror that definition flag into its own column so the filter can report a
-- trigger as disabled for either reason. COALESCE because rows written before the field existed
-- carry no 'sourceDisabled' key; the scheduler rewrites them as it visits each trigger.
--
-- Canonical rationale for the Postgres and MySQL sides of this migration.
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "source_disabled" BOOLEAN NOT NULL GENERATED ALWAYS AS (COALESCE(JQ_BOOLEAN("value", '.sourceDisabled'), FALSE));
