ALTER TYPE queue_type ADD VALUE 'io.kestra.core.models.executions.MetricEntry';

/* ----------------------- metrics ----------------------- */
CREATE TABLE metrics (
    key VARCHAR(30) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS bool)) STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    task_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskId') STORED,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    taskrun_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskRunId') STORED,
    metric_name VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    timestamp TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'timestamp')) STORED
);

CREATE INDEX metrics_flow_id ON logs (deleted, namespace, flow_id);
CREATE INDEX metrics_execution_id ON logs (deleted, execution_id);
CREATE INDEX metrics_timestamp ON logs (deleted, timestamp);