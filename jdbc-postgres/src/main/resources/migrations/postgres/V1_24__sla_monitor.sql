CREATE TABLE IF NOT EXISTS sla_monitor (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    sla_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'slaId') STORED,
    deadline TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'deadline')) STORED
);

CREATE INDEX IF NOT EXISTS sla_monitor__deadline ON sla_monitor (deadline);
CREATE INDEX IF NOT EXISTS sla_monitor__execution_id ON sla_monitor (execution_id);