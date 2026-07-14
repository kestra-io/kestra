-- Widen the dedicated log-store table's generated task_id column to VARCHAR(256).
-- Metadata-only on Postgres (no table rewrite). ${table} is substituted with the configured log table.
ALTER TABLE ${table} ALTER COLUMN task_id TYPE VARCHAR(256);
