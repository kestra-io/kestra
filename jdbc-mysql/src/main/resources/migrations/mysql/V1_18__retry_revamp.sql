ALTER TABLE executions MODIFY COLUMN `state_current` ENUM (
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
) GENERATED ALWAYS AS (value ->> '$.state.current') STORED NOT NULL;