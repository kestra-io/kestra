-- MySQL side of the 2.1.01-missing-indexes fix (see 2.1.01-missing-indexes-postgres.sql /
-- -h2.sql for the reverse gap on flow_topologies).
--
-- Postgres and H2 have indexed multipleconditions since baseline; MySQL never did, only
-- PRIMARY KEY (`key`). AbstractJdbcMultipleConditionStateStore looks up a row by
-- (tenant_id, namespace, flow_id, condition_id) and expires rows by (tenant_id, start_date,
-- end_date), both with FOR UPDATE, so on MySQL every one of those calls was a full table scan
-- taking a lock on every row it scanned. MySQL has no IF NOT EXISTS on CREATE INDEX, so index
-- creation is guarded via information_schema, matching the pattern used in 2.0.02-queue-mysql.sql.

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'multipleconditions' AND index_name = 'ix_namespace__flow_id__condition_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX `ix_namespace__flow_id__condition_id` ON multipleconditions (`tenant_id`, `namespace`, `flow_id`, `condition_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'multipleconditions' AND index_name = 'ix_start_date__end_date');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX `ix_start_date__end_date` ON multipleconditions (`tenant_id`, `start_date`, `end_date`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
