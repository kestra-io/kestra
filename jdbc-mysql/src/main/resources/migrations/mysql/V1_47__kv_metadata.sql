CREATE TABLE IF NOT EXISTS kv_metadata (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `name` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.name') STORED NOT NULL,
    `description` TEXT GENERATED ALWAYS AS (value ->> '$.description') STORED,
    `version` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.version') STORED NOT NULL,
    `last` BOOL GENERATED ALWAYS AS (value ->> '$.last' = 'true') STORED NOT NULL,
    `expiration_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.expirationDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    FULLTEXT ix_fulltext (name)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX ix_last_deleted_tenant_namespace_name_version ON `kv_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `name`, `version`);
CREATE INDEX ix_last_deleted_tenant_namespace_name ON `kv_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `name`);
CREATE INDEX ix_last_deleted_tenant_namespace_version ON `kv_metadata` (`last`, `deleted`, `tenant_id`, `namespace`, `version`);
CREATE INDEX ix_last_deleted_tenant_name_version ON `kv_metadata` (`last`, `deleted`, `tenant_id`, `name`, `version`);
