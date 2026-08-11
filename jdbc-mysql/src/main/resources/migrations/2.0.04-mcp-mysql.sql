CREATE TABLE IF NOT EXISTS `mcp` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_tenant_deleted_id (tenant_id, deleted, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `mcp_session` (
    `key`        VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`      JSON NOT NULL,
    `tenant_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `server_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.serverId') STORED,
    `session_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.sessionId') STORED NOT NULL,
    `sse_node`   VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.sseNode') STORED,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_tenant_server_session (tenant_id, server_id, session_id),
    INDEX ix_sse_node (sse_node),
    INDEX ix_mcp_session__created_at (created_at)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
