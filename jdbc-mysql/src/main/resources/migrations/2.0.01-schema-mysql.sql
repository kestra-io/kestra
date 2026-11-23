-- Kestra 2.0 schema upgrade for MySQL. Applies on top of the frozen 1.3-era baseline
-- (baseline-mysql.sql), whether that came from a fresh install (0-init) or an existing
-- Flyway-managed database. All statements are idempotent: safe to run on both fresh installs
-- and upgrades, and safe to re-run.
--
-- MySQL does not support IF NOT EXISTS / IF EXISTS on CREATE INDEX, ALTER TABLE ADD/DROP COLUMN,
-- so we use information_schema checks with prepared statements for idempotent DDL. Each guarded
-- section sets its probe variable (@col_exists / @idx_exists / @len / @col_key etc.) immediately
-- before reading it -- there is no cross-section dependency on these variable names.

-- Tables removed in 2.0
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS executorstate;

-- Distributed locking. tenant_id lets AbstractJdbcLeaseStore's buildTenantCondition filter
-- lease rows like any tenant-scoped table (server-mutex Locks have none, matching its
-- null-tenant branch); locked_until pushes the lease expiry check into the WHERE clause
-- instead of fetching every row for the category/tenant first.
CREATE TABLE IF NOT EXISTS locks (
    `key`          VARCHAR(700) NOT NULL PRIMARY KEY,
    `value`        JSON         NOT NULL,
    `category`     VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.category') STORED NOT NULL,
    `id`           VARCHAR(500) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `owner`        VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.owner') STORED NOT NULL,
    `tenant_id`    VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `locked_until` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.lockedUntil', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED,
    INDEX ix_category_id (category, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Task outputs
CREATE TABLE IF NOT EXISTS task_outputs (
    `key`          VARCHAR(250) PRIMARY KEY,
    `task_run_id`  VARCHAR(150) NOT NULL,
    `tenant_id`    VARCHAR(150) NOT NULL,
    `execution_id` VARCHAR(150) NOT NULL,
    `value`        LONGBLOB,
    `uri`          VARCHAR(250),
    INDEX task_outputs_execution_id (`execution_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- VNode-based scheduler columns
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
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN `trigger_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.trigger.id'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'idx_executions_trigger_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE executions ADD INDEX idx_executions_trigger_id (`trigger_id`), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
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
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE executions ADD INDEX executions_parent_id (`deleted`, `tenant_id`, `parent_id`), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- MCP servers and sessions
CREATE TABLE IF NOT EXISTS `mcp` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_tenant_deleted_id (tenant_id, deleted, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `mcp_session` (
    `key`        VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`      JSON NOT NULL,
    `tenant_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `server_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.serverId') STORED,
    `session_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.sessionId') STORED NOT NULL,
    `sse_node`   VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.sseNode') STORED,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_tenant_server_session (tenant_id, server_id, session_id),
    INDEX ix_sse_node (sse_node),
    INDEX ix_mcp_session__created_at (created_at)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Triggers: last-triggered date and type
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

-- Full-text search on service_instance
SET @col_hostname = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'service_instance' AND column_name = 'server_hostname');
SET @sql = IF(@col_hostname = 0, 'ALTER TABLE service_instance ADD COLUMN server_hostname VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.server.hostname'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_version = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'service_instance' AND column_name = 'server_version');
SET @sql = IF(@col_version = 0, 'ALTER TABLE service_instance ADD COLUMN server_version VARCHAR(100) GENERATED ALWAYS AS (value ->> ''$.server.version'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'service_instance' AND index_name = 'ix_fulltext');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE service_instance ADD FULLTEXT INDEX ix_fulltext (service_id, service_type, server_hostname, server_version)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the redundant ix_execution_id index: it is a strict leftmost prefix of
-- ix_execution_id__task_id (execution_id, task_id) and provides no extra coverage.
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'logs' AND index_name = 'ix_execution_id');
SET @sql = IF(@idx_exists > 0, 'ALTER TABLE logs DROP INDEX ix_execution_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the redundant ix_timestamp index: every timestamp-range query on logs always carries a
-- tenant_id predicate, so ix_tenant_timestamp (tenant_id, timestamp, level) covers it fully.
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'logs' AND index_name = 'ix_timestamp');
SET @sql = IF(@idx_exists > 0, 'ALTER TABLE logs DROP INDEX ix_timestamp', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Composite index for the flow-scoped logs query (tenant + namespace + flow_id + timestamp range).
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'logs' AND index_name = 'ix_tenant_namespace_flow_id_timestamp');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE logs ADD INDEX ix_tenant_namespace_flow_id_timestamp (tenant_id, namespace, flow_id, timestamp, level), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Flows: draft flag, derived from the JSON value. COALESCE keeps legacy rows without the
-- field published (draft = false).
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'draft');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE `flows` ADD COLUMN `draft` BOOL GENERATED ALWAYS AS (COALESCE(value ->> ''$.draft'' = ''true'', FALSE)) STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'flows' AND index_name = 'ix_flows_draft');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX ix_flows_draft ON `flows` (`deleted`, `draft`, `namespace`, `id`, `revision`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Per-execution and periodically compacted execution-statistics rows, aggregated to the
-- minute (see issue #16524).
CREATE TABLE IF NOT EXISTS execution_statistics (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `state` VARCHAR(50) GENERATED ALWAYS AS (value ->> '$.state') STORED NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED,
    INDEX ix_execution_statistics_tenant_id__date (tenant_id, `date`),
    INDEX ix_execution_statistics_tenant_id__namespace__flow_id__date (tenant_id, namespace, flow_id, `date`),
    -- Fast retrieval of NOT NULL execution_id values ordered by date, for the compaction mechanism
    INDEX ix_execution_statistics_execution_id__date (execution_id, `date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Flows: disabled flag, derived from the JSON value
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'disabled');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE `flows` ADD COLUMN `disabled` BOOL GENERATED ALWAYS AS (COALESCE(value ->> ''$.disabled'' = ''true'', FALSE)) STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Namespace file paths are case-sensitive: they map 1:1 to case-sensitive storage URIs, and the
-- primary key `key` of namespace_file_metadata embeds the path (tenantId_namespace_path_revision).
-- The table was created with the case-insensitive collation utf8mb4_unicode_ci, so on MySQL two
-- files whose paths differ only by case (e.g. MyFile.sql vs myfile.sql) collide: the second one is
-- treated as the same file, and path lookups return the wrong row. See Pylon #2018.
-- Switch `key`, `path` and `parent_path` to the case-sensitive utf8mb4_bin collation.
-- Each ALTER is guarded so the migration is idempotent.

-- `key` (primary key, regular column)
SET @col_key = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'namespace_file_metadata' AND column_name = 'key');
SET @sql = IF(@col_key IS NOT NULL AND @col_key <> 'utf8mb4_bin', 'ALTER TABLE namespace_file_metadata MODIFY COLUMN `key` VARCHAR(768) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- `path` (generated STORED column; also backs the FULLTEXT index and the composite indexes)
SET @col_path = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'namespace_file_metadata' AND column_name = 'path');
SET @sql = IF(@col_path IS NOT NULL AND @col_path <> 'utf8mb4_bin', 'ALTER TABLE namespace_file_metadata MODIFY COLUMN `path` VARCHAR(350) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.path'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- `parent_path` (generated STORED column)
SET @col_parent = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'namespace_file_metadata' AND column_name = 'parent_path');
SET @sql = IF(@col_parent IS NOT NULL AND @col_parent <> 'utf8mb4_bin', 'ALTER TABLE namespace_file_metadata MODIFY COLUMN `parent_path` VARCHAR(350) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.parentPath'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Widen metrics.task_id to match Task.id's @Size(max = 256). Modifying a STORED GENERATED column
-- rebuilds the table (ALGORITHM=COPY), so the ALTER runs only when the column is still shorter
-- than 256, skipping the rebuild for installs already at that width (e.g. arriving from 1.3.x).
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'metrics' AND column_name = 'task_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE metrics MODIFY COLUMN `task_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.taskId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Widen triggers.trigger_id to match Trigger.id's @Size(max = 256), see kestra-ee #9268, and
-- narrow executions.trigger_execution_id to VARCHAR(150) so all dialects match. Modifying a
-- STORED GENERATED column rebuilds the table (ALGORITHM=COPY), so each ALTER runs only when the
-- column is still below its target width. executions.trigger_id already got its final width
-- above; triggers.trigger_id predates 2.0 so there's no create step to fold this into.
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'trigger_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE triggers MODIFY COLUMN `trigger_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.triggerId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'trigger_execution_id');
SET @sql = IF(@len IS NOT NULL AND @len < 150, 'ALTER TABLE executions MODIFY COLUMN `trigger_execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.trigger.variables.executionId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Executions: originating Loop task id (loopRun.taskId), so LOOP-kind iteration executions
-- can be filtered per loop task. Mirrors loop_run_index.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'loop_run_task_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE executions ADD COLUMN loop_run_task_id VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.loopRun.taskId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Merge ix_namespace and ix_flowId into a single composite index: a flow always belongs to a
-- namespace, so (deleted, tenant_id, namespace, flow_id) still serves namespace-only queries
-- through its leftmost prefix while giving namespace + flow_id queries a full four-column seek,
-- and it removes one index from the hottest write table.
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'ix_namespace__flow_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE executions ADD INDEX ix_namespace__flow_id (deleted, tenant_id, namespace, flow_id), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'ix_namespace');
SET @sql = IF(@idx_exists > 0, 'ALTER TABLE executions DROP INDEX ix_namespace', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'executions' AND index_name = 'ix_flowId');
SET @sql = IF(@idx_exists > 0, 'ALTER TABLE executions DROP INDEX ix_flowId', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Fixes the UTC-offset extraction in the multipleconditions date columns. The offset written by
-- the .SSSXXX serializer is always 6 characters (+02:00, -03:30), but the old extraction read only
-- 5: SUBSTRING(v, LENGTH(v) - 5, 5) yields -03:3 for -03:30, which CONVERT_TZ then misreads as
-- -03:03. Offsets whose minutes are 00 survived by accident (the dropped character was a 0), so
-- only non-zero-minute timezones were affected -- Newfoundland, Marquesas, India, Nepal, Adelaide,
-- Iran, Myanmar, Chatham. RIGHT(v, 6) takes the whole offset.
ALTER TABLE multipleconditions MODIFY COLUMN `start_date` DATETIME(6) GENERATED ALWAYS AS (
    IF(
        SUBSTRING(value ->> '$.start', LENGTH(value ->> '$.start'), LENGTH(value ->> '$.start')) = 'Z',
        STR_TO_DATE(value ->> '$.start', '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(value ->> '$.start', 1, LENGTH(value ->> '$.start') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            RIGHT(value ->> '$.start', 6),
            'UTC'
        )
    )
) STORED NOT NULL;

ALTER TABLE multipleconditions MODIFY COLUMN `end_date` DATETIME(6) GENERATED ALWAYS AS (
    IF(
        SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end'), LENGTH(value ->> '$.end')) = 'Z',
        STR_TO_DATE(value ->> '$.end', '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(value ->> '$.end', 1, LENGTH(value ->> '$.end') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            RIGHT(value ->> '$.end', 6),
            'UTC'
        )
    )
) STORED NOT NULL;
