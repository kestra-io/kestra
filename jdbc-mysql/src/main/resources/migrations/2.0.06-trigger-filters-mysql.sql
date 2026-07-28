SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'last_triggered_date');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `last_triggered_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> ''$.lastTriggeredDate'', ''%Y-%m-%dT%H:%i:%s.%fZ'')) STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'type');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `type` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.type'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
