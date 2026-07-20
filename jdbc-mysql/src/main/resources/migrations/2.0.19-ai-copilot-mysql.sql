CREATE TABLE IF NOT EXISTS `ai_agent_thread` (
    `key`       VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`     JSON NOT NULL,
    `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenant') STORED,
    `user_id`   VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.userId') STORED,
    `deleted`   BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `created`   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated`   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_ai_agent_thread__tenant_deleted_user (tenant_id, deleted, user_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_agent_message` (
    `key`        VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`      JSON NOT NULL,
    `tenant_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenant') STORED,
    `thread_id`  VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.threadId') STORED NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_ai_agent_message__tenant_thread (tenant_id, thread_id, `key`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
