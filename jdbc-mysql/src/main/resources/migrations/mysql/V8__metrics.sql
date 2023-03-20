ALTER TABLE queues MODIFY COLUMN `type`
    ENUM(
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
CREATE TABLE metrics (
    `key` VARCHAR(30) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `taskrun_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
    `metric_name` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.name') STORED,
    `timestamp` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.timestamp' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_metrics_flow_id (deleted, namespace, flow_id),
    INDEX ix_metrics_execution_id (deleted, execution_id),
    INDEX ix_metrics_timestamp (deleted, timestamp)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
