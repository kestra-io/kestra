-- Widen the dedicated log-store table's generated trigger_id column to VARCHAR(256) to match Trigger.id's @Size(max = 256).
-- Guarded: modifying a STORED GENERATED column rebuilds the table (ALGORITHM=COPY), so widen only
-- when still shorter than 256. ${table} is substituted with the configured log table name.
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '${table}' AND column_name = 'trigger_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE ${table} MODIFY COLUMN `trigger_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.triggerId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
