-- Fix the executions.state_duration generated column so it stores the TOTAL execution
-- duration in milliseconds.
--
-- The previous expression used EXTRACT(MILLISECONDS FROM interval), which only returns the
-- seconds field of the interval (0-59999) and silently drops minutes and hours. As a result,
-- sorting executions by duration was wrong for any execution lasting >= 1 minute (a 7m run
-- could sort below a 3m run). EXTRACT(EPOCH FROM interval) returns the total number of seconds
-- across all interval fields, so multiplying by 1000 yields the correct total milliseconds.
--
-- state_duration is a STORED generated column, whose expression cannot be altered in place, so
-- the column is dropped and re-created. Re-creation backfills (recomputes) every existing row.
-- This takes an ACCESS EXCLUSIVE lock on the executions table for the duration of the rewrite.
DROP INDEX IF EXISTS executions_state_duration;

ALTER TABLE executions DROP COLUMN IF EXISTS state_duration;

ALTER TABLE executions ADD COLUMN IF NOT EXISTS state_duration BIGINT GENERATED ALWAYS AS ((ROUND(EXTRACT(EPOCH FROM PARSE_ISO8601_DURATION(value #>> '{state, duration}')) * 1000))::bigint) STORED;

CREATE INDEX CONCURRENTLY IF NOT EXISTS executions_state_duration ON executions (deleted, tenant_id, state_duration);
