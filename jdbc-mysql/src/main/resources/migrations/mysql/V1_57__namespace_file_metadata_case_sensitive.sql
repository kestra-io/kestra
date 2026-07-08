-- Namespace file paths are case-sensitive: they map 1:1 to case-sensitive storage URIs, and the
-- primary key `key` of namespace_file_metadata embeds the path (tenantId_namespace_path_version).
-- The table was created with the case-insensitive collation utf8mb4_unicode_ci, so on MySQL two
-- files whose paths differ only by case (e.g. MyFile.sql vs myfile.sql) collide: the primary key
-- collides on insert and path lookups return the wrong row. See Pylon #2018.
-- Switch `key`, `path` and `parent_path` to the case-sensitive utf8mb4_bin collation.

ALTER TABLE namespace_file_metadata
    MODIFY COLUMN `key` VARCHAR(768) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE namespace_file_metadata
    MODIFY COLUMN `path` VARCHAR(350) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> '$.path') STORED NOT NULL;

ALTER TABLE namespace_file_metadata
    MODIFY COLUMN `parent_path` VARCHAR(350) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (value ->> '$.parentPath') STORED;
