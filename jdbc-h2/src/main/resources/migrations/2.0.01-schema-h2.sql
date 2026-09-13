-- Kestra 2.0 schema upgrade for H2. Applies on top of the frozen 1.3-era baseline (baseline-h2.sql),
-- whether that came from a fresh install (0-init) or an existing Flyway-managed database. Every
-- statement is idempotent, so re-running after a partial failure is safe.

-- Tables removed in 2.0
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS executorstate;

-- Distributed locking. tenant_id lets AbstractJdbcLeaseStore's buildTenantCondition filter
-- lease rows like any tenant-scoped table (server-mutex Locks have none, matching its
-- null-tenant branch); locked_until pushes the lease expiry check into the WHERE clause
-- instead of fetching every row for the category/tenant first.
CREATE TABLE IF NOT EXISTS locks (
    "key"          VARCHAR(700) NOT NULL PRIMARY KEY,
    "value"        TEXT         NOT NULL,
    "category"     VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.category')),
    "id"           VARCHAR(500) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "owner"        VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.owner')),
    "tenant_id"    VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "locked_until" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.lockedUntil'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS locks__category_id ON locks ("category", "id");

-- Task outputs
CREATE TABLE IF NOT EXISTS task_outputs (
    "key"          VARCHAR(250) PRIMARY KEY,
    "task_run_id"  VARCHAR(150) NOT NULL,
    "tenant_id"    VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value"        LONGBLOB,
    "uri"          VARCHAR(250)
);

CREATE INDEX IF NOT EXISTS task_outputs_execution_id ON task_outputs ("execution_id");

-- VNode-based scheduler columns
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "vnode"                 INT       GENERATED ALWAYS AS (JQ_INTEGER("value", '.vnode'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "locked"                BOOLEAN   GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.locked'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_epoch" BIGINT    GENERATED ALWAYS AS (JQ_LONG("value", '.nextEvaluationEpoch'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_date"  TIMESTAMP GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26) AS TIMESTAMP));
ALTER TABLE triggers DROP COLUMN IF EXISTS "next_execution_date";

CREATE INDEX IF NOT EXISTS idx_trigger_scheduler            ON triggers ("vnode", "next_evaluation_epoch", "locked");
CREATE INDEX IF NOT EXISTS idx_trigger_next_evaluation_date ON triggers ("next_evaluation_date");

-- Executions: trigger reference
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "trigger_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.id'));
CREATE INDEX IF NOT EXISTS idx_executions_trigger_id ON executions ("trigger_id");

-- Worker 2.0: replace worker_uuid with worker_uid
DROP INDEX IF EXISTS worker_job_running_worker_uuid;
ALTER TABLE worker_job_running DROP COLUMN IF EXISTS "worker_uuid";
ALTER TABLE worker_job_running ADD COLUMN IF NOT EXISTS "worker_uid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.workerInstance.uid'));
CREATE INDEX IF NOT EXISTS worker_job_running_worker_uid ON worker_job_running ("worker_uid");

-- Executions: parent execution ID and loop run index
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "parent_id" VARCHAR(100) GENERATED ALWAYS AS (JQ_STRING("value", '.parentId'));
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_index" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.loopRun.index'));
CREATE INDEX IF NOT EXISTS executions_parent_id ON executions ("deleted", "tenant_id", "parent_id");

-- MCP servers and sessions
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

-- Triggers: last-triggered date and type
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "last_triggered_date" TIMESTAMP GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.lastTriggeredDate'), 26) AS TIMESTAMP));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "type" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.type'));

-- Full-text search on service_instance
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS "fulltext" TEXT GENERATED ALWAYS AS (
    JQ_STRING("value", '.id') || ' ' ||
    JQ_STRING("value", '.type') || ' ' ||
    COALESCE(JQ_STRING("value", '.server.hostname'), '') || ' ' ||
    COALESCE(JQ_STRING("value", '.server.version'), '')
);

-- Drop the redundant logs_execution_id index: it is a strict leftmost prefix of
-- logs_execution_id__task_id (execution_id, task_id) and provides no extra coverage.
DROP INDEX IF EXISTS logs_execution_id;

-- Drop the redundant logs_timestamp index: every timestamp-range query on logs always carries a
-- tenant_id predicate, so logs_tenant_timestamp (tenant_id, timestamp, level) covers it fully.
DROP INDEX IF EXISTS logs_timestamp;

-- Composite index for the flow-scoped logs query (tenant + namespace + flow_id + timestamp range).
CREATE INDEX IF NOT EXISTS logs_tenant_namespace_flow_id_timestamp ON logs ("tenant_id", "namespace", "flow_id", "timestamp", "level");

-- Fix executions_start_date/executions_end_date on H2: add tenant_id so the optimizer can narrow
-- by tenant before scanning the date range, matching the MySQL and Postgres index definitions.
DROP INDEX IF EXISTS executions_start_date;
CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "tenant_id", "start_date");

DROP INDEX IF EXISTS executions_end_date;
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "tenant_id", "end_date");

-- Flows: draft flag, derived from the JSON value. COALESCE keeps legacy rows without the
-- field published (draft = false).
ALTER TABLE flows ADD COLUMN IF NOT EXISTS "draft" BOOLEAN NOT NULL GENERATED ALWAYS AS (COALESCE(JQ_BOOLEAN("value", '.draft'), FALSE));

CREATE INDEX IF NOT EXISTS flows_draft ON flows ("deleted", "draft", "namespace", "id", "revision");

-- Per-execution and periodically compacted execution-statistics rows, aggregated to the
-- minute (see issue #16524).
CREATE TABLE IF NOT EXISTS execution_statistics (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "state" VARCHAR(50) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId'))
);

CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__date ON execution_statistics ("tenant_id", "date");
CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__namespace__flow_id__date ON execution_statistics ("tenant_id", "namespace", "flow_id", "date");
-- Fast retrieval of NOT NULL execution_id values ordered by date, for the compaction mechanism
CREATE INDEX IF NOT EXISTS execution_statistics_execution_id__date ON execution_statistics ("execution_id", "date");

-- Flows: disabled flag, derived from the JSON value
ALTER TABLE flows ADD COLUMN IF NOT EXISTS "disabled" BOOLEAN NOT NULL GENERATED ALWAYS AS (COALESCE(JQ_BOOLEAN("value", '.disabled'), FALSE));

-- Widen metrics.task_id to match Task.id's @Size(max = 256). H2 restates the generation
-- expression when altering a generated column.
ALTER TABLE metrics ALTER COLUMN "task_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId'));

-- Widen triggers.trigger_id to match Trigger.id's @Size(max = 256), and
-- narrow executions.trigger_execution_id to VARCHAR(150) so all dialects match.
ALTER TABLE triggers ALTER COLUMN "trigger_id" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId'));
ALTER TABLE executions ALTER COLUMN "trigger_execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.variables.executionId'));

-- Executions: originating Loop task id (loopRun.taskId), so LOOP-kind iteration executions
-- can be filtered per loop task.
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_task_id" VARCHAR(256) GENERATED ALWAYS AS (JQ_STRING("value", '.loopRun.taskId'));

-- Merge executions_namespace and executions_flow_id into a single composite index: a flow
-- always belongs to a namespace, so (deleted, tenant_id, namespace, flow_id) still serves
-- namespace-only queries through its leftmost prefix while giving namespace + flow_id
-- queries a full seek, and it removes one index from the hottest write table.
CREATE INDEX IF NOT EXISTS executions_namespace__flow_id ON executions ("deleted", "tenant_id", "namespace", "flow_id");

DROP INDEX IF EXISTS executions_namespace;
DROP INDEX IF EXISTS executions_flow_id;

-- Hardening: removes the width dependency in the two trigger date columns. JdbcMapper writes an
-- Instant as uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z' (27 chars), and LEFT(v, 26) drops the trailing 'Z'
-- only at exactly that width; any other width leaves the 'Z' in place, so H2 reads the value as
-- TIMESTAMP WITH TIME ZONE and shifts it by the server's UTC offset on the way into the zoneless
-- column, which is correct only on a server running in UTC.
ALTER TABLE triggers ALTER COLUMN "next_evaluation_date" TIMESTAMP GENERATED ALWAYS AS (CAST(REPLACE(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26), 'Z', '') AS TIMESTAMP));
ALTER TABLE triggers ALTER COLUMN "last_triggered_date" TIMESTAMP GENERATED ALWAYS AS (CAST(REPLACE(LEFT(JQ_STRING("value", '.lastTriggeredDate'), 26), 'Z', '') AS TIMESTAMP));

-- Makes the multipleconditions date columns tolerate any fractional-second width. They parse
-- MultipleConditionWindow.start/end (ZonedDateTime, written by the .SSSXXX serializer, which now
-- emits 6 fractional digits instead of the 3 this pattern assumed). Normalising the fraction to 3
-- digits with a regex keeps the existing SSSXXX pattern working; truncating to milliseconds isn't
-- a regression since that's all the column ever held.
ALTER TABLE multipleconditions ALTER COLUMN "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(REGEXP_REPLACE(JQ_STRING("value", '.start'), '(\.\d{3})\d*', '$1'), 'uuuu-MM-dd''T''HH:mm:ss.SSSXXX'));
ALTER TABLE multipleconditions ALTER COLUMN "end_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(REGEXP_REPLACE(JQ_STRING("value", '.end'), '(\.\d{3})\d*', '$1'), 'uuuu-MM-dd''T''HH:mm:ss.SSSXXX'));
