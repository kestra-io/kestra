-- Add a generated column derived from the JSON value so the SQL "latest non-draft revision"
-- query can filter on it directly. COALESCE keeps legacy rows whose JSON does not yet carry
-- the field as published (draft = false).
--
-- MySQL does not support IF NOT EXISTS on ALTER TABLE ADD COLUMN / CREATE INDEX, so we guard
-- both statements with information_schema checks to keep the migration idempotent (safe on
-- partial-failure recovery and force-rerun), matching the other MySQL migrations.
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
