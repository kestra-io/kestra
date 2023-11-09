ALTER TABLE executions ADD COLUMN parent_id VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.parentId') STORED;

CREATE INDEX ix_parent_id ON executions (`deleted`, `tenant_id`, `parent_id`);