-- Adds the task_run_statistics table, used to store per-task-run (raw) and periodically
-- compacted (aggregate) task-run-statistic rows, aggregated to the minute.
CREATE TABLE IF NOT EXISTS task_run_statistics (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "state" VARCHAR(50) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "task_run_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId'))
);

CREATE INDEX IF NOT EXISTS task_run_statistics_tenant_id__date ON task_run_statistics ("tenant_id", "date");
CREATE INDEX IF NOT EXISTS task_run_statistics_tenant_id__namespace__flow_id__task_id__date ON task_run_statistics ("tenant_id", "namespace", "flow_id", "task_id", "date");
-- Index used by the compaction mechanism for fast retrieval of raw rows (NOT NULL task_run_id) ordered by date
CREATE INDEX IF NOT EXISTS task_run_statistics_task_run_id__date ON task_run_statistics ("task_run_id", "date");