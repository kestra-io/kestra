-- Widen the dedicated log-store table's generated task_id column to VARCHAR(256).
-- H2 restates the generation expression when altering a generated column. ${table} is the log table.
ALTER TABLE ${table} ALTER COLUMN "task_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId'));
