CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__tenant_deleted_id ON mcp ("tenant_id", "deleted", "id");

CREATE TABLE IF NOT EXISTS mcp_session (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      TEXT NOT NULL,
    "tenant_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "server_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.serverId')),
    "session_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.sessionId')),
    "sse_node"   VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.sseNode')),
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp_session__tenant_server_session ON mcp_session ("tenant_id", "server_id", "session_id");
CREATE INDEX IF NOT EXISTS mcp_session__sse_node ON mcp_session ("sse_node");
CREATE INDEX IF NOT EXISTS mcp_session__created_at ON mcp_session ("created_at");
