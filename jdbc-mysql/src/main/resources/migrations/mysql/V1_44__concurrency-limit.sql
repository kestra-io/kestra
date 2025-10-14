CREATE TABLE IF NOT EXISTS concurrency_limit (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `running` INT GENERATED ALWAYS AS (value ->> '$.running') STORED NOT NULL,
    INDEX ix_flow (tenant_id, namespace, flow_id)
);

DROP TABLE IF EXISTS execution_running;

DELETE FROM queues WHERE type = 'io.kestra.core.runners.ExecutionRunning';

ALTER TABLE queues MODIFY COLUMN `type` ENUM(
    'io.kestra.core.models.executions.Execution',
    'io.kestra.core.models.templates.Template',
    'io.kestra.core.models.executions.ExecutionKilled',
    'io.kestra.core.runners.WorkerJob',
    'io.kestra.core.runners.WorkerTaskResult',
    'io.kestra.core.runners.WorkerInstance',
    'io.kestra.core.runners.WorkerTaskRunning',
    'io.kestra.core.models.executions.LogEntry',
    'io.kestra.core.models.triggers.Trigger',
    'io.kestra.ee.models.audits.AuditLog',
    'io.kestra.core.models.executions.MetricEntry',
    'io.kestra.core.runners.WorkerTriggerResult',
    'io.kestra.core.runners.SubflowExecutionResult',
    'io.kestra.core.server.ClusterEvent',
    'io.kestra.core.runners.SubflowExecutionEnd',
    'io.kestra.core.models.flows.FlowInterface',
    'io.kestra.core.runners.MultipleConditionEvent'
) NOT NULL;