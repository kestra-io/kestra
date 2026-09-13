-- Fethr catch-up: replays upstream Flyway migrations V1_45-V1_57, which this fork never applied
-- because it used those version numbers for its own features. Runs before 2.0.01-schema, which
-- assumes the repaired schema.
--
-- Every statement is idempotent: on a fresh install "0-init" has already done all of this, and on
-- an already-caught-up database each statement is a no-op.
--
-- The functions used below (FULLTEXT_INDEX, PARSE_ISO8601_DATETIME, UPDATE_UPDATED_DATETIME) all
-- predate the fork point and already exist from V1_1__initial.

/* --- Deliberately omitted ------------------------------------------------------------------
 * V1_48 executions.state_duration DROP NOT NULL  -> 2.0.12-fix-state-duration drops and
 *                                                   recreates the column outright.
 * V1_57 widen task_id                            -> 2.0.01-schema widens metrics.task_id and
 *                                                   2.0.06-widen-logs widens the log table's
 *                                                   (the default log table is "logs").
 * V1_51 triggers.disabled                        -> "disabled" is not in
 *                                                   QueryFilter.Resource.TRIGGER's supported
 *                                                   fields; 2.0 never queries it.
 * V1_52 queue_type asset values                  -> EE-only, no OSS reference.
 * V1_56 flows.updated                            -> no field("updated") anywhere in jdbc/.
 * ------------------------------------------------------------------------------------------ */


/* --- V1_45, V1_47: state values that 2.0's State.Type emits ------------------------------- */
-- Without these, any execution reaching SUBMITTED or RESUBMITTED fails to persist: state_current
-- is a generated column cast to state_type.
ALTER TYPE state_type ADD VALUE IF NOT EXISTS 'SUBMITTED';
ALTER TYPE state_type ADD VALUE IF NOT EXISTS 'RESUBMITTED';


/* --- V1_53: drop the "deleted" columns 2.0's models no longer write ------------------------ */
-- LogEntry and MetricEntry both dropped their `deleted` field in 2.0. These are NOT NULL generated
-- columns over value->>'deleted', so leaving them in place makes every log and metric insert fail
-- on a null violation. The indexes are dropped first because they are prefixed by the column.
DROP INDEX IF EXISTS logs_execution_id;
DROP INDEX IF EXISTS logs_execution_id__task_id;
DROP INDEX IF EXISTS logs_execution_id__taskrun_id;
DROP INDEX IF EXISTS logs_namespace_flow;

ALTER TABLE logs DROP COLUMN IF EXISTS "deleted";

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;

ALTER TABLE metrics DROP COLUMN IF EXISTS "deleted";


/* --- V1_53, V1_54, V1_55: the surviving log and metric index set --------------------------- */
-- Collapsed to the end state rather than replaying three scripts' worth of drop/recreate churn.
--
-- logs_execution_id and logs_timestamp are deliberately NOT recreated: 2.0.01-schema drops both
-- immediately afterwards as redundant (a strict leftmost prefix of logs_execution_id__task_id, and
-- fully covered by logs_tenant_timestamp respectively). Rebuilding them on a large logs table only
-- to drop them again is pure cost.
--
-- logs_namespace_flow is likewise gone for good: V1_55 replaced it with the two tenant-prefixed
-- indexes below, and 2.0.01-schema adds logs_tenant_namespace_flow_id_timestamp on top.
-- A CREATE INDEX CONCURRENTLY that fails midway leaves an INVALID index behind, and a re-run's
-- IF NOT EXISTS would then silently skip it, leaving the index permanently unusable. Drop any
-- invalid leftovers first. Same guard 2.0.01-schema applies to its own concurrent index.
DO $$
DECLARE
    idx TEXT;
BEGIN
    FOR idx IN
        SELECT c.relname
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE NOT i.indisvalid
          AND c.relname IN (
              'logs_execution_id__task_id', 'logs_execution_id__taskrun_id',
              'logs_tenant_timestamp', 'logs_tenant_namespace_timestamp',
              'metrics_flow_id', 'metrics_execution_id', 'metrics_timestamp'
          )
    LOOP
        EXECUTE 'DROP INDEX ' || quote_ident(idx);
    END LOOP;
END $$;

CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_execution_id__task_id ON logs ("execution_id", "task_id");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_execution_id__taskrun_id ON logs ("execution_id", "taskrun_id");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");

CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_flow_id ON metrics ("tenant_id", "namespace", "flow_id");
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_execution_id ON metrics ("execution_id");
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_timestamp ON metrics ("tenant_id", "timestamp");


/* --- V1_46, V1_49: kv_metadata ------------------------------------------------------------- */
-- Registered in JdbcTableConfigsFactory as "kvmetadata" -> PersistedKvMetadata. DDL lifted from
-- 2.0's baseline-postgres.sql rather than from upstream V1_46, so the table matches what 2.0
-- expects rather than what 1.3 created (V1_49's "created" column is already folded in there).
CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    "description" TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "expiration_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'expirationDate')) STORED,
    "created" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(COALESCE(value ->> 'created', value ->> 'updated'))) STORED,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'name' AS varchar))
    ) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");

CREATE OR REPLACE TRIGGER kv_metadata_updated BEFORE UPDATE
    ON kv_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* --- V1_50: namespace_file_metadata -------------------------------------------------------- */
-- Registered in JdbcTableConfigsFactory as "namespacefilemetadata" -> NamespaceFileMetadata.
CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'path') STORED,
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (value ->> 'parentPath') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'size' AS BIGINT)) STORED,
    "created" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'created')) STORED,
    "updated" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updated')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (FULLTEXT_INDEX(CAST(value ->> 'path' AS varchar))) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");

CREATE OR REPLACE TRIGGER namespace_file_metadata_updated BEFORE UPDATE
    ON namespace_file_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
