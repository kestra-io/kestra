CREATE TABLE IF NOT EXISTS concurrency_slot_monitor (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    deadline TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'deadline')) STORED
);

CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__deadline ON concurrency_slot_monitor (deadline);
CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__execution_id ON concurrency_slot_monitor (execution_id);
CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__flow ON concurrency_slot_monitor (tenant_id, namespace, flow_id);
