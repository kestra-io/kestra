-- MySQL side of the 2.1.07-notification-items-owner migration; see
-- 2.1.07-notification-items-owner-h2.sql for why the column exists.
--
-- MySQL has no IF NOT EXISTS on ADD COLUMN or CREATE INDEX, so both are guarded via information_schema.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'notification_items' AND column_name = 'owner');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE notification_items ADD COLUMN `owner` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.owner'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notification_items' AND index_name = 'notification_items_owner_outcome');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX `notification_items_owner_outcome` ON notification_items (`owner`, `outcome`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
