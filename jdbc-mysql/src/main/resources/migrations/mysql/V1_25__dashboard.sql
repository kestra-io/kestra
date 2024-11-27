CREATE TABLE IF NOT EXISTS `dashboards` (
                                       `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `title` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.title') STORED NOT NULL,
    `description` TEXT GENERATED ALWAYS AS (value ->> '$.description') STORED,
    `source_code` TEXT NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_tenant (deleted, tenant_id),
    FULLTEXT ix_fulltext (title)
    ) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;