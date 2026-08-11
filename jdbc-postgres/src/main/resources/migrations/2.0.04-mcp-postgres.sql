CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOLEAN)) STORED,
    "created" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__tenant_deleted_id ON mcp ("tenant_id", "deleted", "id");

CREATE OR REPLACE TRIGGER mcp_updated BEFORE UPDATE
    ON mcp FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();

CREATE TABLE IF NOT EXISTS mcp_session (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      JSONB NOT NULL,
    "tenant_id"  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "server_id"  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'serverId') STORED,
    "session_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'sessionId') STORED,
    "sse_node"   VARCHAR(250) GENERATED ALWAYS AS (value ->> 'sseNode') STORED,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp_session__tenant_server_session ON mcp_session ("tenant_id", "server_id", "session_id");
CREATE INDEX IF NOT EXISTS mcp_session__sse_node ON mcp_session ("sse_node");
CREATE INDEX IF NOT EXISTS mcp_session__created_at ON mcp_session ("created_at");
