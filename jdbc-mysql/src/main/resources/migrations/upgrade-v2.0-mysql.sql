-- V2.0 upgrade migration for MySQL
-- Applies schema changes introduced in Kestra 2.0 on top of an existing Flyway-managed schema (Kestra <= 1.3).
-- All statements are idempotent: safe to run on both fresh installs and Flyway upgrades.
--
-- MySQL does not support IF NOT EXISTS / IF EXISTS on CREATE INDEX, ALTER TABLE ADD/DROP COLUMN,
-- so we use information_schema checks with prepared statements for idempotent DDL.

-- Tables removed in 2.0
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS executorstate;

-- New table: distributed locking
CREATE TABLE IF NOT EXISTS locks (
    `key`      VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`    JSON         NOT NULL,
    `category` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.category') STORED NOT NULL,
    `id`       VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `owner`    VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.owner') STORED NOT NULL,
    INDEX ix_category_id (category, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- New table: task outputs
CREATE TABLE IF NOT EXISTS task_outputs (
    `key`          VARCHAR(250) PRIMARY KEY,
    `task_run_id`  VARCHAR(150) NOT NULL,
    `tenant_id`    VARCHAR(150) NOT NULL,
    `execution_id` VARCHAR(150) NOT NULL,
    `value`        LONGBLOB,
    `uri`          VARCHAR(250),
    INDEX task_outputs_execution_id (`execution_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Scheduler 2.0: VNode-based scheduler columns on triggers
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'vnode');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `vnode` INT GENERATED ALWAYS AS (CAST(value ->> ''$.vnode'' AS SIGNED)) STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'locked');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `locked` BOOL GENERATED ALWAYS AS (value ->> ''$.locked'' = ''true'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'next_evaluation_epoch');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `next_evaluation_epoch` BIGINT GENERATED ALWAYS AS (CAST(value ->> ''$.nextEvaluationEpoch'' AS SIGNED)) STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'next_evaluation_date');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE triggers ADD COLUMN `next_evaluation_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> ''$.nextEvaluationDate'', ''%Y-%m-%dT%H:%i:%s.%fZ'')) STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- next_execution_date existed in the Flyway schema; dropping it also removes its index
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'next_execution_date');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE triggers DROP COLUMN `next_execution_date`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'triggers' AND index_name = 'idx_trigger_scheduler');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_trigger_scheduler ON triggers (vnode, next_evaluation_epoch, locked)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'triggers' AND index_name = 'idx_trigger_next_evaluation_date');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_trigger_next_evaluation_date ON triggers (next_evaluation_date)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Executions: trigger reference
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'trigger_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.trigger.id'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'idx_executions_trigger_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_executions_trigger_id ON executions (`trigger_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Worker 2.0: replace worker_uuid with worker_uid
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'worker_job_running' AND index_name = 'worker_job_running_worker_uuid');
SET @sql = IF(@idx_exists > 0, 'DROP INDEX worker_job_running_worker_uuid ON worker_job_running', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'worker_job_running' AND column_name = 'worker_uuid');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE worker_job_running DROP COLUMN `worker_uuid`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'worker_job_running' AND column_name = 'worker_uid');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE worker_job_running ADD COLUMN `worker_uid` VARCHAR(36) GENERATED ALWAYS AS (value ->> ''$.workerInstance.uid'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'worker_job_running' AND index_name = 'worker_job_running_worker_uid');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX worker_job_running_worker_uid ON worker_job_running (`worker_uid`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Executions: parent execution ID and loop run index
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'parent_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN parent_id VARCHAR(100) GENERATED ALWAYS AS (value ->> ''$.parentId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'loop_run_index');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN loop_run_index INT GENERATED ALWAYS AS (value ->> ''$.loopRun.index'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'executions_parent_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX executions_parent_id ON executions (`deleted`, `tenant_id`, `parent_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
