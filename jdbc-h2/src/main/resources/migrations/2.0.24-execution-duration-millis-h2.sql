-- 2.0.24: executions.state_duration was generated in seconds (the raw .state.duration JSON value), while every
-- consumer (dashboard charts divide by 1000, statistics use Duration.ofMillis) and the Postgres and MySQL columns
-- use milliseconds. Regenerate the column in milliseconds.
DROP INDEX IF EXISTS executions_state_duration;
ALTER TABLE executions DROP COLUMN IF EXISTS "state_duration";
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "state_duration" FLOAT GENERATED ALWAYS AS (JQ_DOUBLE("value", '.state.duration') * 1000);
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions ("deleted", "tenant_id", "state_duration");
