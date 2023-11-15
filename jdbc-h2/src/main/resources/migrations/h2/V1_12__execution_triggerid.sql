ALTER TABLE executions ADD COLUMN IF NOT EXISTS "trigger_execution_id" VARCHAR(100) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.variables.executionId'));

CREATE INDEX IF NOT EXISTS executions_trigger_execution_id ON executions ("deleted", "tenant_id", "trigger_execution_id");
