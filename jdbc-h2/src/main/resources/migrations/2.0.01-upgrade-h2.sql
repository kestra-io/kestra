-- V2.0 upgrade migration for H2
-- Applies schema changes introduced in Kestra 2.0 on top of an existing Flyway-managed schema (Kestra <= 1.3).
-- Only executed when upgrading from Flyway; skipped on fresh installations (handled by the 0-init script).

-- Tables removed in 2.0
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS executorstate;

-- New table: distributed locking
CREATE TABLE IF NOT EXISTS locks (
    "key"      VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"    TEXT         NOT NULL,
    "category" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.category')),
    "id"       VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "owner"    VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.owner'))
);

CREATE INDEX IF NOT EXISTS locks__category_id ON locks ("category", "id");

-- New table: task outputs
CREATE TABLE IF NOT EXISTS task_outputs (
    "key"          VARCHAR(250) PRIMARY KEY,
    "task_run_id"  VARCHAR(150) NOT NULL,
    "tenant_id"    VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value"        LONGBLOB,
    "uri"          VARCHAR(250)
);

CREATE INDEX IF NOT EXISTS task_outputs_execution_id ON task_outputs ("execution_id");

-- Scheduler 2.0: VNode-based scheduler columns on triggers
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "vnode"                 INT       GENERATED ALWAYS AS (JQ_INTEGER("value", '.vnode'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "locked"                BOOLEAN   GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.locked'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_epoch" BIGINT    GENERATED ALWAYS AS (JQ_LONG("value", '.nextEvaluationEpoch'));
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "next_evaluation_date"  TIMESTAMP GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26) AS TIMESTAMP));
ALTER TABLE triggers DROP COLUMN IF EXISTS "next_execution_date";

CREATE INDEX IF NOT EXISTS idx_trigger_scheduler            ON triggers ("vnode", "next_evaluation_epoch", "locked");
CREATE INDEX IF NOT EXISTS idx_trigger_next_evaluation_date ON triggers ("next_evaluation_date");

-- Executions: trigger reference
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.id'));
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
