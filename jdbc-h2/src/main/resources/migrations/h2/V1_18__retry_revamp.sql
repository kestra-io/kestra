ALTER TABLE executions ALTER COLUMN "state_current" ENUM (
    'CREATED',
    'RUNNING',
    'PAUSED',
    'RESTARTED',
    'KILLING',
    'SUCCESS',
    'WARNING',
    'FAILED',
    'KILLED',
    'CANCELLED',
    'QUEUED',
    'RETRYING'
) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state.current'));