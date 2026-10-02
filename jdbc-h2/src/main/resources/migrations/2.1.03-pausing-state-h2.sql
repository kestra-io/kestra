-- Add the PAUSING execution state (kestra-io/kestra-ee#9838): an execution runs its Approval
-- task's onWait tasks in this state, before moving to PAUSED. H2 restates the generation
-- expression when altering a generated column, same as 2.0.06-widen-logs-h2.sql; this works in
-- place even with the executions_state_current index present.
ALTER TABLE executions ALTER COLUMN "state_current" ENUM (
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
) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state.current'));
