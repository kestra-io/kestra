-- Widen the metrics.task_id generated column from 150 to 256 to match Task.id's @Size(max = 256).
-- Metadata-only on Postgres (no table rewrite); NOT NULL is preserved.
ALTER TABLE metrics ALTER COLUMN task_id TYPE VARCHAR(256);
