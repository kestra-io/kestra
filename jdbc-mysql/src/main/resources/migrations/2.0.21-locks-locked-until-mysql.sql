-- generated column lets AbstractJdbcLeaseStore push the lockedUntil expiry check into SQL instead of fetching every row
-- MySQL lacks IF NOT EXISTS on ALTER TABLE ADD COLUMN, so guard via information_schema for idempotency
-- generated columns disallow stored functions (ER_GENERATED_COLUMN_FUNCTION_IS_NOT_ALLOWED), so inline STR_TO_DATE like executions.start_date/end_date; JdbcMapper always emits a trailing 'Z', so no CONVERT_TZ needed
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'locks' AND column_name = 'locked_until');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE `locks` ADD COLUMN `locked_until` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> ''$.lockedUntil'', ''%Y-%m-%dT%H:%i:%s.%fZ'')) STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
