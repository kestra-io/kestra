ALTER TABLE executions ADD COLUMN IF NOT EXISTS trigger_execution_id VARCHAR(150) GENERATED ALWAYS AS (value #>> '{trigger, variables, executionId}') STORED;

CREATE INDEX IF NOT EXISTS executions_trigger_execution_id ON executions ("deleted", "tenant_id", "trigger_execution_id");
