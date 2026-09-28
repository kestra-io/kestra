-- Add the PAUSING execution state (kestra-io/kestra-ee#9838): an execution runs its Approval
-- task's onWait tasks in this state, before moving to PAUSED. MODIFY must restate the full
-- generated column definition, matching the pattern in 2.0.06-widen-logs-mysql.sql.
ALTER TABLE executions MODIFY COLUMN `state_current` ENUM(
    'CREATED',
    'RUNNING',
    'PAUSING',
    'PAUSED',
    'RESTARTED',
    'KILLING',
    'SUCCESS',
    'WARNING',
    'FAILED',
    'KILLED',
    'CANCELLED',
    'QUEUED',
    'RETRYING',
    'RETRIED',
    'SKIPPED',
    'BREAKPOINT',
    'SUBMITTED',
    'RESUBMITTED'
) GENERATED ALWAYS AS (value ->> '$.state.current') STORED NOT NULL;
