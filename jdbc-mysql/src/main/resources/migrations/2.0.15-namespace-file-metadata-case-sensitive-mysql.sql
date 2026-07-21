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
