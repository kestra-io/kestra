-- Widen the dedicated log-store table's generated task_id and trigger_id columns to VARCHAR(256),
-- to match Task.id's / Trigger.id's @Size(max = 256). H2 restates the generation expression when
-- altering a generated column; re-running to the same width is a no-op. ${table} is the log table.

ALTER TABLE ${table} ALTER COLUMN "task_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId'));

ALTER TABLE ${table} ALTER COLUMN "trigger_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId'));
