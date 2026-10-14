-- Kestra 2.0 schema upgrade for PostgreSQL. Applies on top of the frozen 1.3-era baseline
-- (baseline-postgres.sql), whether that came from a fresh install (0-init) or an existing
-- Flyway-managed database. Every statement is idempotent, so re-running after a partial
-- failure is safe.

-- Tables removed in 2.0
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS executorstate;

-- Distributed locking. tenant_id lets AbstractJdbcLeaseStore's buildTenantCondition filter
-- lease rows like any tenant-scoped table (server-mutex Locks have none, matching its
-- null-tenant branch); locked_until pushes the lease expiry check into the WHERE clause
-- instead of fetching every row for the category/tenant first.
CREATE TABLE IF NOT EXISTS locks (
    key          VARCHAR(700) NOT NULL PRIMARY KEY,
    value        JSONB        NOT NULL,
    category     VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'category') STORED,
    id           VARCHAR(500) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    owner        VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'owner') STORED,
    tenant_id    VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    locked_until TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'lockedUntil')) STORED
);

CREATE INDEX IF NOT EXISTS locks__catefory_id ON locks (category, id);

-- Task outputs
CREATE TABLE IF NOT EXISTS task_outputs (
    "key"          VARCHAR(250) PRIMARY KEY,
    "task_run_id"  VARCHAR(150) NOT NULL,
    "tenant_id"    VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value"        BYTEA,
    "uri"          VARCHAR(250)
);

CREATE INDEX IF NOT EXISTS task_outputs_execution_id ON task_outputs ("execution_id");

-- VNode-based scheduler columns
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "vnode"                 INTEGER    GENERATED ALWAYS AS (CAST(value ->> 'vnode' AS INTEGER)) STORED;
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "locked"                BOOLEAN    GENERATED ALWAYS AS (CAST(value ->> 'locked' AS BOOLEAN)) STORED;
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_epoch" BIGINT     GENERATED ALWAYS AS (CAST(value ->> 'nextEvaluationEpoch' AS BIGINT)) STORED;
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_date"  TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'nextEvaluationDate')) STORED;
ALTER TABLE triggers DROP COLUMN IF EXISTS "next_execution_date";

CREATE INDEX IF NOT EXISTS idx_trigger_scheduler            ON triggers (vnode, next_evaluation_epoch, locked);
CREATE INDEX IF NOT EXISTS idx_trigger_next_evaluation_date ON triggers (next_evaluation_date);

-- Executions: trigger reference
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "trigger_id" VARCHAR(256) GENERATED ALWAYS AS (value -> 'trigger' ->> 'id') STORED;

-- A failed CREATE INDEX CONCURRENTLY leaves an invalid index that IF NOT EXISTS then skips
-- forever, so drop it first if a previous partial run left one behind.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'idx_executions_trigger_id' AND NOT i.indisvalid
    ) THEN
        EXECUTE 'DROP INDEX idx_executions_trigger_id';
    END IF;
END $$;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_executions_trigger_id ON executions ("trigger_id") WHERE "trigger_id" IS NOT NULL;

-- Worker 2.0: replace worker_uuid with worker_uid
DROP INDEX IF EXISTS worker_job_running_worker_uuid;
ALTER TABLE worker_job_running DROP COLUMN IF EXISTS "worker_uuid";
ALTER TABLE worker_job_running ADD COLUMN IF NOT EXISTS "worker_uid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value -> 'workerInstance' ->> 'uid') STORED;
CREATE INDEX IF NOT EXISTS worker_job_running_worker_uid ON worker_job_running (worker_uid);

-- Executions: parent execution ID and loop run index
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "parent_id" VARCHAR(100) GENERATED ALWAYS AS (value #>> '{parentId}') STORED;
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_index" INT GENERATED ALWAYS AS ((value #>> '{loopRun,index}')::INT) STORED;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'executions_parent_id' AND NOT i.indisvalid
    ) THEN
        EXECUTE 'DROP INDEX executions_parent_id';
    END IF;
END $$;
CREATE INDEX CONCURRENTLY IF NOT EXISTS executions_parent_id ON executions ("deleted", "tenant_id", "parent_id") WHERE "parent_id" IS NOT NULL;

-- MCP servers and sessions
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

-- Triggers: last-triggered date and type
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "last_triggered_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'lastTriggeredDate')) STORED;
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "type" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'type') STORED;

-- Full-text search on service_instance
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS fulltext TSVECTOR GENERATED ALWAYS AS (
    FULLTEXT_INDEX(CAST(value ->> 'id' AS varchar)) ||
    FULLTEXT_INDEX(CAST(value ->> 'type' AS varchar)) ||
    FULLTEXT_INDEX(COALESCE(CAST(value -> 'server' ->> 'hostname' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value -> 'server' ->> 'version' AS varchar), ''))
) STORED;

CREATE INDEX IF NOT EXISTS service_instance_fulltext ON service_instance USING GIN (fulltext);

-- Drop the redundant logs_execution_id index: strict leftmost prefix of
-- logs_execution_id__task_id, no extra coverage.
DROP INDEX CONCURRENTLY IF EXISTS logs_execution_id;

-- Drop the redundant logs_timestamp index: every timestamp-range query on logs carries a
-- tenant_id predicate, so logs_tenant_timestamp (tenant_id, timestamp, level) covers it fully.
DROP INDEX CONCURRENTLY IF EXISTS logs_timestamp;

-- Composite index for the flow-scoped logs query (tenant + namespace + flow_id + timestamp range).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'logs_tenant_namespace_flow_id_timestamp' AND NOT i.indisvalid
    ) THEN
        EXECUTE 'DROP INDEX logs_tenant_namespace_flow_id_timestamp';
    END IF;
END $$;
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_namespace_flow_id_timestamp ON logs ("tenant_id", "namespace", "flow_id", "timestamp", "level");

-- Flows: draft flag, derived from the JSON value. COALESCE keeps legacy rows without the
-- field published (draft = false).
ALTER TABLE flows ADD COLUMN IF NOT EXISTS draft BOOL NOT NULL GENERATED ALWAYS AS (COALESCE(CAST(value ->> 'draft' AS BOOL), FALSE)) STORED;

CREATE INDEX IF NOT EXISTS flows_draft ON flows (deleted, draft, namespace, id, revision);

-- Per-execution and periodically compacted execution-statistics rows, aggregated to the
-- minute (see issue #16524).
CREATE TABLE IF NOT EXISTS execution_statistics (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    state VARCHAR(50) NOT NULL GENERATED ALWAYS AS (value ->> 'state') STORED,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED
);

CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__date ON execution_statistics (tenant_id, date);
CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__namespace__flow_id__date ON execution_statistics (tenant_id, namespace, flow_id, date);
-- Fast retrieval of NOT NULL execution_id values ordered by date, for the compaction mechanism
CREATE INDEX IF NOT EXISTS execution_statistics_execution_id__date ON execution_statistics (execution_id, date);

-- Flows: disabled flag, derived from the JSON value
ALTER TABLE flows ADD COLUMN IF NOT EXISTS disabled BOOL NOT NULL GENERATED ALWAYS AS (COALESCE(CAST(value ->> 'disabled' AS BOOL), FALSE)) STORED;

-- Widen metrics.task_id to match Task.id's @Size(max = 256). Metadata-only on Postgres.
ALTER TABLE metrics ALTER COLUMN task_id TYPE VARCHAR(256);

-- Widen triggers.trigger_id to match Trigger.id's @Size(max = 256), see kestra-ee #9268.
-- Metadata-only on Postgres. executions.trigger_id already got its final width above;
-- triggers.trigger_id predates 2.0 so there's no create step to fold this into.
ALTER TABLE triggers ALTER COLUMN trigger_id TYPE VARCHAR(256);

-- Executions: originating Loop task id (loopRun.taskId), so LOOP-kind iteration executions
-- can be filtered per loop task. Mirrors loop_run_index.
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_task_id" VARCHAR(256) GENERATED ALWAYS AS (value #>> '{loopRun,taskId}') STORED;

-- Merge executions_namespace and executions_flow_id into a single composite index: a flow
-- always belongs to a namespace, so (deleted, tenant_id, namespace, flow_id) still serves
-- namespace-only queries through its leftmost prefix while giving namespace + flow_id
-- queries a full seek, and it removes one index from the hottest write table.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'executions_namespace__flow_id' AND NOT i.indisvalid
    ) THEN
        EXECUTE 'DROP INDEX executions_namespace__flow_id';
    END IF;
END $$;

CREATE INDEX CONCURRENTLY IF NOT EXISTS executions_namespace__flow_id ON executions ("deleted", "tenant_id", "namespace", "flow_id");

DROP INDEX CONCURRENTLY IF EXISTS executions_namespace;
DROP INDEX CONCURRENTLY IF EXISTS executions_flow_id;
