CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `path` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.path') STORED NOT NULL,
    `parent_path` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.parentPath') STORED,
    `version` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.version') STORED NOT NULL,
    `last` BOOL GENERATED ALWAYS AS (value ->> '$.last' = 'true') STORED NOT NULL,
    `size` BIGINT UNSIGNED GENERATED ALWAYS AS (value ->> '$.size') STORED NOT NULL,
    `created` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.created' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updated' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    FULLTEXT ix_fulltext (path)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX ix_last_deleted_tenant_namespace_path_version ON `namespace_file_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `path`, `version`);
CREATE INDEX ix_last_deleted_tenant_namespace_path ON `namespace_file_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `path`);
CREATE INDEX ix_last_deleted_tenant_namespace_parent_path ON `namespace_file_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `parent_path`);
CREATE INDEX ix_last_deleted_tenant_namespace_version ON `namespace_file_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `version`);
CREATE INDEX ix_last_deleted_tenant_path_version ON `namespace_file_metadata` (`last`, `deleted`, `tenant_id`, `path`, `version`);
