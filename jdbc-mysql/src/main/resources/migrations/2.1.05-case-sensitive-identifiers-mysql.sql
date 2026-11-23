-- `flows`, `triggers` and `kv_metadata` were created with the case-insensitive collation
-- utf8mb4_unicode_ci, so on MySQL two identifiers that differ only by case (company.a vs company.A)
-- resolve to the same row: the second write overwrites the first, and a lookup under one casing returns
-- the entity stored under the other. H2 and PostgreSQL compare case-sensitively and are unaffected.
-- Switch the primary key and every identifier column to the case-sensitive utf8mb4_bin collation.
-- Free-text search stays case-insensitive through an explicit collation in the search conditions.
--
-- `triggers.execution_id` is converted too: every column of the FULLTEXT index must share one collation.
-- Each table is rebuilt with a single ALTER (ALGORITHM=COPY, as for any STORED generated column
-- change), guarded so the migration is idempotent.

SET @col = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'namespace');
SET @sql = IF(@col IS NOT NULL AND @col <> 'utf8mb4_bin', 'ALTER TABLE flows
    MODIFY COLUMN `key` VARCHAR(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY COLUMN `id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.id'') STORED NOT NULL,
    MODIFY COLUMN `namespace` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.namespace'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'triggers' AND column_name = 'namespace');
SET @sql = IF(@col IS NOT NULL AND @col <> 'utf8mb4_bin', 'ALTER TABLE triggers
    MODIFY COLUMN `key` VARCHAR(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY COLUMN `namespace` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.namespace'') STORED NOT NULL,
    MODIFY COLUMN `flow_id` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.flowId'') STORED NOT NULL,
    MODIFY COLUMN `trigger_id` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.triggerId'') STORED NOT NULL,
    MODIFY COLUMN `execution_id` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.executionId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col = (SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kv_metadata' AND column_name = 'namespace');
SET @sql = IF(@col IS NOT NULL AND @col <> 'utf8mb4_bin', 'ALTER TABLE kv_metadata
    MODIFY COLUMN `key` VARCHAR(768) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY COLUMN `namespace` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.namespace'') STORED NOT NULL,
    MODIFY COLUMN `name` VARCHAR(350) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> ''$.name'') STORED NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
