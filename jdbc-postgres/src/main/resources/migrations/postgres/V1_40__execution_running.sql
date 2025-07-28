CREATE TABLE IF NOT EXISTS execution_running (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED
);

CREATE INDEX IF NOT EXISTS execution_running__flow ON execution_running (tenant_id, namespace, flow_id);

ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.runners.ExecutionRunning';