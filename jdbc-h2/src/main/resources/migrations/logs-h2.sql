/* ----------------------- logs (external log store) ----------------------- */
/*
 * Standalone, idempotent DDL for the H2 log store, run against the log datasource by
 * V2_0LogsMigration when `kestra.logs.type` is h2/memory. Mirrors the `logs` table + final index
 * set from baseline-h2.sql (+ 2.0.11), with the table name templated via ${table} so a custom
 * `kestra.logs.h2.table` is honoured. Idempotent (IF NOT EXISTS) so it no-ops on an existing table.
 */
-- Prerequisite H2 functions for the generated columns below. Needed when the log store runs on a
-- dedicated database that has not been through baseline-h2.sql. Idempotent.
CREATE ALIAS IF NOT EXISTS JQ_STRING FOR "io.kestra.runner.h2.H2Functions.jqString";
CREATE ALIAS IF NOT EXISTS JQ_INTEGER FOR "io.kestra.runner.h2.H2Functions.jqInteger";

CREATE TABLE IF NOT EXISTS ${table} (
    "key" VARCHAR(30) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
    "attempt_number" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.attemptNumber')),
    "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "message" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.message')),
    "thread" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.thread')),
    "level" ENUM (
        'ERROR',
        'WARN',
        'INFO',
        'DEBUG',
        'TRACE'
    ) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.level')),
    "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.timestamp'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.namespace') ||
        JQ_STRING("value", '.flowId') ||
        COALESCE(JQ_STRING("value", '.taskId'), '') ||
        COALESCE(JQ_STRING("value", '.executionId'), '') ||
        COALESCE(JQ_STRING("value", '.taskRunId'), '') ||
        COALESCE(JQ_STRING("value", '.triggerId'), '') ||
        COALESCE(JQ_STRING("value", '.message'), '') ||
        COALESCE(JQ_STRING("value", '.thread'), '')
    ),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.executionKind'))
);

CREATE INDEX IF NOT EXISTS ${table}_execution_id__task_id ON ${table} ("execution_id", "task_id");
CREATE INDEX IF NOT EXISTS ${table}_execution_id__taskrun_id ON ${table} ("execution_id", "taskrun_id");
CREATE INDEX IF NOT EXISTS ${table}_tenant_timestamp ON ${table} ("tenant_id", "timestamp", "level");
CREATE INDEX IF NOT EXISTS ${table}_tenant_namespace_timestamp ON ${table} ("tenant_id", "namespace", "timestamp", "level");
CREATE INDEX IF NOT EXISTS ${table}_tenant_namespace_flow_id_timestamp ON ${table} ("tenant_id", "namespace", "flow_id", "timestamp", "level");
