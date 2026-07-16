-- 2.0.17: widen every primary-datasource generated column that holds a Trigger.id to VARCHAR(256) to match
-- Trigger.id's @Size(max = 256). See kestra-ee #9268. Metadata-only on Postgres (no table rewrite); NOT NULL
-- is preserved. trigger_execution_id is already VARCHAR(150) on Postgres, so it is left as-is.
ALTER TABLE triggers ALTER COLUMN trigger_id TYPE VARCHAR(256);
ALTER TABLE executions ALTER COLUMN trigger_id TYPE VARCHAR(256);
