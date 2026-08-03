-- 2.0.21: merge ix_namespace (deleted, tenant_id, namespace) and ix_flowId (deleted, tenant_id,
-- flow_id) into a single composite index. A flow always belongs to a namespace, so
-- (deleted, tenant_id, namespace, flow_id) still serves namespace-only queries through its
-- leftmost prefix while giving namespace + flow_id queries a full four-column seek, and it removes
-- one index from the hottest write table.

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
