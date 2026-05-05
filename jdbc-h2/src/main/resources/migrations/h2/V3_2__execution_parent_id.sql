ALTER TABLE executions ADD COLUMN IF NOT EXISTS "parent_id" VARCHAR(100) GENERATED ALWAYS AS (JQ_STRING("value", '.parentId'));
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_index" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.loopRun.index'));

CREATE INDEX IF NOT EXISTS executions_parent_id ON executions ("deleted", "tenant_id", "parent_id");