CREATE TABLE ${prefix}queues (
    `offset` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type` ENUM(
        'io.kestra.core.models.executions.Execution',
        'io.kestra.core.models.flows.Flow',
        'io.kestra.core.models.templates.Template',
        'io.kestra.core.models.executions.ExecutionKilled',
        'io.kestra.core.runners.WorkerTask',
        'io.kestra.core.runners.WorkerTaskResult',
        'io.kestra.core.runners.WorkerInstance',
        'io.kestra.core.runners.WorkerTaskRunning',
        'io.kestra.core.models.executions.LogEntry',
        'io.kestra.core.models.triggers.Trigger'
    ) NOT NULL,
    `key` VARCHAR(250) NOT NULL,
    `value` JSON NOT NULL,
    `consumers` SET(
        'indexer',
        'executor',
        'worker'
    )
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


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


