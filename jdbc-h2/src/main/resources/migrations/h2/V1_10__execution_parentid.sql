ALTER TABLE executions ADD COLUMN IF NOT EXISTS "parent_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.parentId'));

CREATE INDEX IF NOT EXISTS executions_parent_id ON executions ("deleted", "tenant_id", "parent_id");
