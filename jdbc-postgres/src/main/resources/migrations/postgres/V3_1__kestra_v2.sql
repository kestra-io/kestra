-- Template removal
DROP TABLE IF EXISTS templates;

-- Locks
CREATE TABLE IF NOT EXISTS locks (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    category VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'category') STORED,
    id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    owner VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'owner') STORED
);
CREATE INDEX IF NOT EXISTS locks__catefory_id ON locks (category, id);

-- Executor 2.0: remove executor state as it's not used anymore
DROP TABLE IF EXISTS executorstate;

-- Scheduler 2.0:  VNodes based scheduler
ALTER TABLE triggers ADD "vnode" INTEGER GENERATED ALWAYS AS (CAST(value ->> 'vnode' AS INTEGER)) STORED;
ALTER TABLE triggers ADD "locked" BOOLEAN GENERATED ALWAYS AS (CAST(value ->> 'locked' AS BOOLEAN)) STORED;
ALTER TABLE triggers ADD "next_evaluation_epoch" BIGINT GENERATED ALWAYS AS (CAST(value ->> 'nextEvaluationEpoch' AS BIGINT)) STORED;
ALTER TABLE triggers ADD "next_evaluation_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'nextEvaluationDate')) STORED;

CREATE INDEX idx_trigger_scheduler ON triggers (vnode, next_evaluation_epoch, locked);
CREATE INDEX idx_trigger_next_evaluation_date ON triggers (next_evaluation_date);

ALTER TABLE executions ADD "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (value -> 'trigger' ->> 'id') STORED;
CREATE INDEX idx_executions_trigger_id ON executions (trigger_id);

-- Queue 2.0: recreate the table
DROP TABLE IF EXISTS queues;
DROP TYPE IF EXISTS queue_type;

CREATE TABLE IF NOT EXISTS queues (
    "offset" SERIAL PRIMARY KEY,
    type INT NOT NULL,
    "routing_key" VARCHAR(250),
    key VARCHAR(250) NOT NULL,
    value JSONB NOT NULL,
    created TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset ON queues ("type", "offset");
CREATE INDEX IF NOT EXISTS queues_created ON queues ("created");

-- Worker 2.0: Change worker_job_running.worker_uuid to extract 'uid' instead of 'workerUuid' from workerInstance
DROP INDEX IF EXISTS worker_job_running_worker_uuid;

ALTER TABLE worker_job_running DROP COLUMN worker_uuid;
ALTER TABLE worker_job_running ADD COLUMN worker_uid VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value -> 'workerInstance' ->> 'uid') STORED;

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uid ON worker_job_running (worker_uid);

-- Store execution outputs in a separate table
CREATE TABLE IF NOT EXISTS task_outputs (
    "key" VARCHAR(250) PRIMARY KEY,
    "task_run_id" VARCHAR(150) NOT NULL,
    "tenant_id" VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value" BYTEA,
    "uri" VARCHAR(250)
);

CREATE INDEX IF NOT EXISTS task_outputs_execution_id ON task_outputs ("execution_id");