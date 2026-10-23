-- Triggers: cover the trigger type in the scheduler index.
-- The scheduler's eligibility query rejects the triggers it never evaluates by type; without the column in the
-- index that rejection needs the row, even though the trigger can never be scheduled.
DROP INDEX IF EXISTS idx_trigger_scheduler;
CREATE INDEX IF NOT EXISTS idx_trigger_scheduler ON triggers (vnode, next_evaluation_epoch, locked, type);
