-- Triggers: cover the trigger type in the scheduler index.
-- The scheduler's eligibility query rejects the triggers it never evaluates by type; without the column in the
-- index that rejection needs the row, even though the trigger can never be scheduled.
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'triggers' AND index_name = 'idx_trigger_scheduler');
SET @sql = IF(@idx_exists > 0, 'DROP INDEX idx_trigger_scheduler ON triggers', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_trigger_scheduler ON triggers (vnode, next_evaluation_epoch, locked, type);
