-- Widen the metrics.task_id generated column from 150 to 256 to match Task.id's @Size(max = 256).
-- H2 restates the generation expression when altering a generated column.
ALTER TABLE metrics ALTER COLUMN "task_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId'));
