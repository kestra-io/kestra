-- MySQL side of the 2.1.02-trigger-source-disabled migration; see
-- 2.1.02-trigger-source-disabled-h2.sql for why the column exists.
--
-- MySQL has no IF NOT EXISTS on ADD COLUMN, so it is guarded via information_schema, matching the
-- pattern used in 2.0.01-schema-mysql.sql.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'source_disabled');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `source_disabled` BOOL GENERATED ALWAYS AS (COALESCE(value ->> ''$.sourceDisabled'' = ''true'', FALSE)) STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
