ALTER TABLE queues
ALTER COLUMN "type" ENUM(
        'io.kestra.core.models.executions.Execution',
        'io.kestra.core.models.flows.Flow',
        'io.kestra.core.models.templates.Template',
        'io.kestra.core.models.executions.ExecutionKilled',
        'io.kestra.core.runners.WorkerTask',
        'io.kestra.core.runners.WorkerTaskResult',
        'io.kestra.core.runners.WorkerInstance',
        'io.kestra.core.runners.WorkerTaskRunning',
        'io.kestra.core.models.executions.LogEntry',
        'io.kestra.core.models.triggers.Trigger',
        'io.kestra.core.models.executions.MetricEntry'
    ) NOT NULL;

/* ----------------------- metrics ----------------------- */
CREATE TABLE IF NOT EXISTS metrics (
     "key" VARCHAR(30) NOT NULL PRIMARY KEY,
     "value" TEXT NOT NULL,
     "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
     "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
     "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
     "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
     "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
     "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
     "metric_name" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
     "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.timestamp'), 'yyyy-MM-dd''T''HH:mm:ss.SSS''Z'''))
);

ALTER TABLE metrics ADD COLUMN IF NOT EXISTS "metric_value" DOUBLE GENERATED ALWAYS AS (JQ_DOUBLE("value", '.value'));

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;
CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("deleted", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics ("deleted", "execution_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("deleted", "timestamp");