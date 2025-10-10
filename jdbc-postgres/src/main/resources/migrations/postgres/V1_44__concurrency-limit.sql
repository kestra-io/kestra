CREATE TABLE IF NOT EXISTS concurrency_limit (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    running INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'running' AS INTEGER)) STORED
);

CREATE INDEX IF NOT EXISTS concurrency_limit__flow ON concurrency_limit (tenant_id, namespace, flow_id);

DROP TABLE IF EXISTS execution_running;