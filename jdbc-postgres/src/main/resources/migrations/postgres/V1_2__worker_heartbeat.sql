/* ----------------------- workerInstance ----------------------- */
CREATE TABLE IF NOT EXISTS worker_instance (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    worker_uuid VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value ->> 'workerUuid') STORED,
    hostname VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'hostname') STORED,
    port INTEGER GENERATED ALWAYS AS (CAST(value ->> 'port' AS INTEGER)) STORED,
    management_port INTEGER GENERATED ALWAYS AS (CAST(value ->> 'managementPort'AS INTEGER)) STORED,
    worker_group VARCHAR(150) GENERATED ALWAYS AS (value ->> 'workerGroup') STORED,
    status VARCHAR(10) NOT NULL GENERATED ALWAYS AS (value ->> 'status') STORED,
    heartbeat_date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'heartbeatDate')) STORED
    );

/* ----------------------- worker_job_running ----------------------- */
CREATE TABLE IF NOT EXISTS worker_job_running (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    worker_uuid VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value -> 'workerInstance' ->> 'workerUuid') STORED,
    taskrun_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value -> 'taskRun' ->> 'id') STORED
);

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uuid ON worker_job_running (worker_uuid);