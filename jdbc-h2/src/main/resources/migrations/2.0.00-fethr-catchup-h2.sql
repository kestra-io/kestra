-- Fethr catch-up (H2): replays the upstream Flyway migrations this fork never applied, because it
-- used those version numbers for its own features. Runs before 2.0.01-schema.
--
-- Derived from upstream's H2 scripts, not translated from the PostgreSQL ones: past V1_53 upstream
-- numbers the two backends independently (H2's V1_54 is logs_indexes, V1_55 flows_updated_date,
-- V1_56 widen_task_id, V1_57 executions_state_duration_millis), and H2 spells the state enum as an
-- inline column type rather than a named type.
--
-- Every statement is idempotent: on a fresh install "0-init" has already done all of this.

/* --- Deliberately omitted ------------------------------------------------------------------
 * H2 V1_57 executions_state_duration_millis -> 2.0.24-execution-duration-millis-h2 supersedes it.
 * H2 V1_56 widen_task_id                    -> 2.0.06-widen-logs-h2 covers the log table.
 * H2 V1_51 triggers.disabled                -> not in QueryFilter.Resource.TRIGGER; unused by 2.0.
 * H2 V1_52 assets_queues                    -> EE-only.
 * H2 V1_55 flows_updated_date               -> no field("updated") anywhere in jdbc/.
 * ------------------------------------------------------------------------------------------ */


/* --- H2 V1_45, V1_47: state values that 2.0's State.Type emits ----------------------------- */
-- H2 has no named enum type, so the column is redeclared with the full final value list. This is
-- declarative, so re-running it is harmless. The list matches 2.0's baseline-h2.sql exactly.
ALTER TABLE executions ALTER COLUMN "state_current" ENUM (
    'CREATED',
    'RUNNING',
    'PAUSED',
    'RESTARTED',
    'KILLING',
    'SUCCESS',
    'WARNING',
    'FAILED',
    'KILLED',
    'CANCELLED',
    'QUEUED',
    'RETRYING',
    'RETRIED',
    'SKIPPED',
    'BREAKPOINT',
    'SUBMITTED',
    'RESUBMITTED'
) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state.current'));


/* --- H2 V1_53: drop the "deleted" columns 2.0's models no longer write --------------------- */
-- LogEntry and MetricEntry both dropped their `deleted` field in 2.0. These are NOT NULL generated
-- columns, so leaving them in place makes every log and metric insert fail on a null violation.
-- H2 will not drop a column an index still references, so the indexes go first.
DROP INDEX IF EXISTS logs_execution_id;
DROP INDEX IF EXISTS logs_execution_id__task_id;
DROP INDEX IF EXISTS logs_execution_id__taskrun_id;
DROP INDEX IF EXISTS logs_namespace_flow;

ALTER TABLE logs DROP COLUMN IF EXISTS "deleted";

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;

ALTER TABLE metrics DROP COLUMN IF EXISTS "deleted";


/* --- H2 V1_53, V1_54: the surviving log and metric index set ------------------------------- */
-- As on PostgreSQL, logs_execution_id and logs_timestamp are not recreated: 2.0.01-schema drops
-- both as redundant right afterwards. H2 has no CREATE INDEX CONCURRENTLY.
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs ("execution_id", "task_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs ("execution_id", "taskrun_id");
CREATE INDEX IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");

CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("tenant_id", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics ("execution_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("tenant_id", "timestamp");


/* --- H2 V1_46, V1_49: kv_metadata ---------------------------------------------------------- */
-- Registered in JdbcTableConfigsFactory as "kvmetadata" -> PersistedKvMetadata. DDL lifted from
-- 2.0's baseline-h2.sql so the table matches what 2.0 expects rather than what 1.3 created.
CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "expiration_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.expirationDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSSSSS''Z''')),
    "created" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(COALESCE(JQ_STRING("value", '.created'), JQ_STRING("value", '.updated')), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name'))
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");


/* --- H2 V1_50: namespace_file_metadata ----------------------------------------------------- */
-- Registered in JdbcTableConfigsFactory as "namespacefilemetadata" -> NamespaceFileMetadata.
CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path')),
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (JQ_STRING("value", '.parentPath')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.size')),
    "created" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.created'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updated'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path'))
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");
