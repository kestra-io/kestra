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

-- Add a composite index for the flow-scoped logs query (tenant + namespace + flow_id + timestamp
-- range). Fixes full-scan on large log datasets when filtering by namespace, flow_id and timestamp.
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'logs' AND index_name = 'ix_tenant_namespace_flow_id_timestamp');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE logs ADD INDEX ix_tenant_namespace_flow_id_timestamp (tenant_id, namespace, flow_id, timestamp, level), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
