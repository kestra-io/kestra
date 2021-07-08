CREATE TABLE `${prefix}flows` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL ,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `revision` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.revision') STORED NOT NULL,
    `source_code` TEXT NOT NULL,
    INDEX ix_id (id),
    INDEX ix_namespace (namespace),
    INDEX ix_revision (revision),
    INDEX ix_deleted (deleted),
    FULLTEXT ix_fulltext (namespace, id),
    FULLTEXT ix_source_code (source_code)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE `${prefix}templates` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL ,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    INDEX ix_id (id),
    INDEX ix_namespace (namespace),
    INDEX ix_deleted (deleted),
    FULLTEXT ix_fulltext (namespace, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


