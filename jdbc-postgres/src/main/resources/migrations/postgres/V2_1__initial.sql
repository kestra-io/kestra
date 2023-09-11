/* ----------------------- workerHeartbeat ----------------------- */
CREATE TABLE IF NOT EXISTS worker_heartbeat (
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


DROP INDEX IF EXISTS worker_heartbeat_worker_uuid;
DROP INDEX IF EXISTS worker_heartbeat_hostname;
DROP INDEX IF EXISTS worker_heartbeat_worker_group;
DROP INDEX IF EXISTS worker_heartbeat_status;
DROP INDEX IF EXISTS worker_heartbeat_date;
CREATE INDEX IF NOT EXISTS worker_heartbeat_status ON worker_heartbeat (status);
CREATE INDEX IF NOT EXISTS worker_heartbeat_date ON worker_heartbeat (heartbeat_date);
CREATE INDEX IF NOT EXISTS worker_heartbeat_hostname ON worker_heartbeat (hostname);
CREATE INDEX IF NOT EXISTS worker_heartbeat_worker_uuid ON worker_heartbeat (worker_uuid);
CREATE INDEX IF NOT EXISTS worker_heartbeat_worker_group ON worker_heartbeat (worker_group);