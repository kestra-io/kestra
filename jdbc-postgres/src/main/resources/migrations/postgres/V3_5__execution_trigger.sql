ALTER TABLE executions ADD "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (value -> 'trigger' ->> 'id') STORED;
CREATE INDEX idx_executions_trigger_id ON executions (trigger_id);
