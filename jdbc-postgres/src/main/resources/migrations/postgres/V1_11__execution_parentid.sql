ALTER TABLE executions ADD COLUMN IF NOT EXISTS parent_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'parentId') STORED;

CREATE INDEX IF NOT EXISTS templates_parent_id ON executions ("deleted", "tenant_id", "parent_id");
