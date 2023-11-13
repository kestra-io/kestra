ALTER TABLE executions ADD COLUMN trigger_execution_id VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.trigger.variables.executionId') STORED;

CREATE INDEX ix_trigger_execution_id ON executions (`deleted`, `tenant_id`, `trigger_execution_id`);