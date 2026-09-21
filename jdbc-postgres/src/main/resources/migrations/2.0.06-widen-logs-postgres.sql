-- Widen the dedicated log-store table's generated task_id and trigger_id columns to VARCHAR(256),
-- to match Task.id's / Trigger.id's @Size(max = 256). Metadata-only on Postgres (no table
-- rewrite). ${table} is substituted with the configured log table.

ALTER TABLE ${table} ALTER COLUMN task_id TYPE VARCHAR(256);

ALTER TABLE ${table} ALTER COLUMN trigger_id TYPE VARCHAR(256);
