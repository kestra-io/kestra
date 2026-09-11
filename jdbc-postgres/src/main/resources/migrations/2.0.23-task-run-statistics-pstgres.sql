-- Adds the task_run_statistics table, used to store per-task-run (raw) and periodically
-- compacted (aggregate) task-run-statistic rows, aggregated to the minute.
CREATE TABLE IF NOT EXISTS task_run_statistics (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    task_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskId') STORED,
    state VARCHAR(50) NOT NULL GENERATED ALWAYS AS (value ->> 'state') STORED,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    task_run_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'taskRunId') STORED
);

CREATE INDEX IF NOT EXISTS task_run_statistics_tenant_id__date ON task_run_statistics (tenant_id, date);
CREATE INDEX IF NOT EXISTS task_run_statistics_tenant_id__namespace__flow_id__task_id__date ON task_run_statistics (tenant_id, namespace, flow_id, task_id, date);
-- Index used for the compaction mechanism for fast retrieval of NOT NULL task_run_id values ordered by date
CREATE INDEX IF NOT EXISTS task_run_statistics_task_run_id__date ON task_run_statistics (task_run_id, date);