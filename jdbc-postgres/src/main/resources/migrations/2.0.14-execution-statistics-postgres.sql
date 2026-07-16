-- Adds the execution_statistics table, used to store per-execution (raw) and periodically
-- compacted (aggregate) execution-statistic rows, aggregated to the minute (see issue #16524).
CREATE TABLE IF NOT EXISTS execution_statistics (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    state VARCHAR(50) NOT NULL GENERATED ALWAYS AS (value ->> 'state') STORED,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED
);

CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__date ON execution_statistics (tenant_id, date);
CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__namespace__flow_id__date ON execution_statistics (tenant_id, namespace, flow_id, date);
-- Index used for the compaction mechanism for fast retrieval of NOT NULL execution_id values ordered by date
CREATE INDEX IF NOT EXISTS execution_statistics_execution_id__date ON execution_statistics (execution_id, date);