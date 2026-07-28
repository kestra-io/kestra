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
