/* ----------------------- workerInstance ----------------------- */
CREATE TABLE IF NOT EXISTS worker_instance (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "worker_uuid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.workerUuid')),
    "hostname" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.hostname')),
    "port" INT GENERATED ALWAYS AS (JQ_INTEGER("value",'.port')),
    "management_port" INT GENERATED ALWAYS AS (JQ_INTEGER("value",'.managementPort')),
    "worker_group" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value",'.workerGroup')),
    "status" VARCHAR(10) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.status')),
    "heartbeat_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.heartbeatDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

/* ----------------------- worker_job_running ----------------------- */
CREATE TABLE IF NOT EXISTS worker_job_running (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "worker_uuid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.workerInstance.workerUuid')),
    "taskrun_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value",'.taskRun.id'))
);

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uuid ON worker_job_running ("worker_uuid");