-- 2.1.06: Add NOT NULL constraint to tenant_id on all tenant-scoped tables.
-- Idempotent: checks IS_NULLABLE = 'YES' before modifying each STORED generated column.

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE flows MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'executions' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE executions MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE triggers MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'logs' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE logs MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'metrics' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE metrics MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'execution_queued' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE execution_queued MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'execution_statistics' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE execution_statistics MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'multipleconditions' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE multipleconditions MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dashboards' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE dashboards MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'concurrency_limit' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE concurrency_limit MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'namespace_file_metadata' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE namespace_file_metadata MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kv_metadata' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE kv_metadata MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Note: `locks` (cluster-wide mutexes) and `notifications` / `notification_items` (user-global alerts)
-- intentionally allow NULL tenant_id and are not constrained.

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'mcp' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE mcp MODIFY COLUMN `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'mcp_session' AND column_name = 'tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE mcp_session MODIFY COLUMN `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flow_topologies' AND column_name = 'source_tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE flow_topologies MODIFY COLUMN `source_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.source.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flow_topologies' AND column_name = 'destination_tenant_id');
SET @sql = IF(@nullable = 'YES', 'ALTER TABLE flow_topologies MODIFY COLUMN `destination_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> ''$.destination.tenantId'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
