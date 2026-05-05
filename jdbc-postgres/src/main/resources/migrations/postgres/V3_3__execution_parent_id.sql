ALTER TABLE executions ADD COLUMN IF NOT EXISTS "parent_id" VARCHAR(100) GENERATED ALWAYS AS (value #>> '{parentId}') STORED;
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_index" INT GENERATED ALWAYS AS ((value #>> '{loopRun,index}')::INT) STORED;

CREATE INDEX IF NOT EXISTS executions_parent_id ON executions ("deleted", "tenant_id", "parent_id");
