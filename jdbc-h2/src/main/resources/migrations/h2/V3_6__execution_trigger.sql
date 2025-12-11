ALTER TABLE executions ADD COLUMN "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.id'));
CREATE INDEX idx_executions_trigger_id ON executions ("trigger_id");
