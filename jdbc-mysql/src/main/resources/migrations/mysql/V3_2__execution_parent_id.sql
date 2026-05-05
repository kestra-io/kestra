ALTER TABLE executions ADD COLUMN parent_id VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.parentId') STORED;
ALTER TABLE executions ADD COLUMN loop_run_index INT GENERATED ALWAYS AS (value ->> '$.loopRun.index') STORED;

CREATE INDEX executions_parent_id ON executions (`deleted`, `tenant_id`, `parent_id`);
