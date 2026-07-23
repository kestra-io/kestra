-- 2.0.18: expose the originating Loop task id (loopRun.taskId) as a queryable generated column on
-- executions, so LOOP-kind iteration executions can be filtered per loop task. Mirrors loop_run_index.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'loop_run_task_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN loop_run_task_id VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.loopRun.taskId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
