-- Queue 2.0 upgrade for MySQL. Builds the final Queue 2.0 shape directly and converges any
-- pre-existing shape to it: a legacy table has already been dropped by
-- 2.0.02-queue-drop-legacy-mysql.sql before this runs (so CREATE below establishes it fresh); a
-- table from baseline-queue-mysql.sql (fresh install, `type` still INT, old index names) or an
-- already-migrated 2.0.0-rcN table (already final) both converge here without ever losing a row.
-- MySQL has no IF EXISTS/IF NOT EXISTS on CREATE/DROP INDEX, so those steps are guarded via
-- information_schema, matching the pattern used elsewhere in these migrations.

CREATE TABLE IF NOT EXISTS queues (
    `offset`      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type`        VARCHAR(250) NOT NULL,
    `routing_key` VARCHAR(250),
    `key`         VARCHAR(250) NOT NULL,
    `value`       JSON         NOT NULL,
    `created`     TIMESTAMP    NOT NULL,
    INDEX `ix_type__routing_key__offset` (`type`, `routing_key`, `offset`),
    INDEX `ix_type__created` (`type`, `created`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Converge a fresh baseline table (type INT) to the final width; a no-op once already VARCHAR.
SET @type_kind = (SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'queues' AND column_name = 'type');
SET @sql = IF(@type_kind IS NOT NULL AND @type_kind <> 'varchar', 'ALTER TABLE queues MODIFY COLUMN `type` VARCHAR(250)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Converge the index set to its final shape (a fresh baseline table still has the old names).
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'queues' AND index_name = 'ix_type__created');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX `ix_type__created` ON queues (`type`, `created`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'queues' AND index_name = 'ix_type__offset');
SET @sql = IF(@idx_exists > 0, 'DROP INDEX `ix_type__offset` ON queues', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'queues' AND index_name = 'ix_created');
SET @sql = IF(@idx_exists > 0, 'DROP INDEX `ix_created` ON queues', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'queues' AND index_name = 'ix_created__type');
SET @sql = IF(@idx_exists > 0, 'DROP INDEX `ix_created__type` ON queues', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
