-- Add the PAUSING execution state (kestra-io/kestra-ee#9838): an execution runs its Approval
-- task's onWait tasks in this state, before moving to PAUSED. state_type is a named enum used
-- only by executions.state_current, so widening it here needs no column-level ALTER. BEFORE
-- keeps the enum's internal ordering consistent with the MySQL and H2 migrations, where PAUSING
-- is inserted between RUNNING and PAUSED rather than appended at the end.
ALTER TYPE state_type ADD VALUE IF NOT EXISTS 'PAUSING' BEFORE 'PAUSED';
