-- 2.0.17: widen every primary-datasource generated column that holds a Trigger.id to VARCHAR(256) to match
-- Trigger.id's @Size(max = 256), and normalize executions.trigger_execution_id to VARCHAR(150) so all
-- dialects match. See kestra-ee #9268. H2 restates the generation expression when altering a generated
-- column; all changes here are index-safe (widenings, plus a narrowing of the ULID-only trigger_execution_id).
ALTER TABLE triggers ALTER COLUMN "trigger_id" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId'));
ALTER TABLE executions ALTER COLUMN "trigger_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.id'));
ALTER TABLE executions ALTER COLUMN "trigger_execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.variables.executionId'));
