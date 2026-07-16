-- 2.0.17: widen every primary-datasource generated column that holds a Trigger.id to VARCHAR(256) to match
-- Trigger.id's @Size(max = 256), and normalize executions.trigger_execution_id to VARCHAR(150) so all
-- dialects match. See kestra-ee #9268. Modifying a STORED GENERATED column rebuilds the table
-- (ALGORITHM=COPY), so each ALTER is guarded by an information_schema length check and runs only when the
-- column is still below its target width.
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'trigger_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE triggers MODIFY COLUMN `trigger_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.triggerId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'trigger_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE executions MODIFY COLUMN `trigger_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.trigger.id'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'trigger_execution_id');
SET @sql = IF(@len IS NOT NULL AND @len < 150, 'ALTER TABLE executions MODIFY COLUMN `trigger_execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.trigger.variables.executionId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
