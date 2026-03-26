-- Template removal
DROP TABLE IF EXISTS templates;

-- Locks
CREATE TABLE IF NOT EXISTS locks (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "category" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.category')),
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "owner" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.owner'))
);
CREATE INDEX IF NOT EXISTS locks__category_id ON locks ("category", "id");

-- Executor 2.0: remove executor state as it's not used anymore
DROP TABLE IF EXISTS executorstate;

-- Scheduler 2.0:  VNodes based scheduler
ALTER TABLE triggers ADD COLUMN "vnode" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.vnode'));
ALTER TABLE triggers ADD COLUMN "locked" BOOLEAN GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.locked'));
ALTER TABLE triggers ADD COLUMN "next_evaluation_epoch" BIGINT GENERATED ALWAYS AS (JQ_LONG("value", '.nextEvaluationEpoch'));
ALTER TABLE triggers ADD COLUMN "next_evaluation_date" TIMESTAMP GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26) AS TIMESTAMP));
ALTER TABLE triggers DROP COLUMN "next_execution_date";

CREATE INDEX idx_trigger_scheduler ON triggers ("vnode", "next_evaluation_epoch", "locked");
CREATE INDEX idx_trigger_next_evaluation_date ON triggers ("next_evaluation_date");

ALTER TABLE executions ADD COLUMN "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.id'));
CREATE INDEX idx_executions_trigger_id ON executions ("trigger_id");

-- Queue 2.0: recreate the table
DROP TABLE IF EXISTS queues;
CREATE TABLE IF NOT EXISTS queues (
    "offset" BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type" INT NOT NULL,
    "routing_key" VARCHAR(250),
    "key" VARCHAR(250) NOT NULL,
    "value" TEXT NOT NULL,
    "created" TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset ON queues ("type", "offset");
CREATE INDEX IF NOT EXISTS queues_created ON queues ("created");

-- Worker 2.0: Change worker_job_running.worker_uuid to extract 'uid' instead of 'workerUuid' from workerInstance
DROP INDEX IF EXISTS worker_job_running_worker_uuid;
ALTER TABLE worker_job_running DROP COLUMN "worker_uuid";
ALTER TABLE worker_job_running ADD COLUMN "worker_uid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.workerInstance.uid'));
CREATE INDEX IF NOT EXISTS worker_job_running_worker_uid ON worker_job_running ("worker_uid");

-- Store execution outputs in a separate table
CREATE TABLE IF NOT EXISTS task_outputs (
    "key" VARCHAR(250) PRIMARY KEY,
    "task_run_id" VARCHAR(150) NOT NULL,
    "tenant_id" VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value" LONGBLOB,
    "uri" VARCHAR(250)
);
CREATE INDEX IF NOT EXISTS task_outputs_execution_id ON task_outputs ("execution_id");