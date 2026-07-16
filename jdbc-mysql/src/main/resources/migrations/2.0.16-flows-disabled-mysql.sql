-- Add a generated `disabled` column derived from the JSON value.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'disabled');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE `flows` ADD COLUMN `disabled` BOOL GENERATED ALWAYS AS (COALESCE(value ->> ''$.disabled'' = ''true'', FALSE)) STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
