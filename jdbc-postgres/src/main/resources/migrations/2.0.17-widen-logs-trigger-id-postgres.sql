-- Widen the dedicated log-store table's generated trigger_id column to VARCHAR(256) to match Trigger.id's @Size(max = 256).
-- Metadata-only on Postgres (no table rewrite). ${table} is substituted with the configured log table.
ALTER TABLE ${table} ALTER COLUMN trigger_id TYPE VARCHAR(256);
