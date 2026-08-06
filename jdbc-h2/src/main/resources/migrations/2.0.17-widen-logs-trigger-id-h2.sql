-- Widen the dedicated log-store table's generated trigger_id column to VARCHAR(256) to match Trigger.id's @Size(max = 256).
-- H2 restates the generation expression when altering a generated column. ${table} is the log table.
ALTER TABLE ${table} ALTER COLUMN "trigger_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId'));
