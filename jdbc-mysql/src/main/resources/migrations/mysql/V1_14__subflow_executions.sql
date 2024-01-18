ALTER TABLE workertaskexecutions RENAME TO subflow_executions;

ALTER TABLE queues MODIFY COLUMN `type`ENUM(
    'io.kestra.core.models.executions.Execution',
    'io.kestra.core.models.flows.Flow',
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
    'io.kestra.core.runners.SubflowExecutionResult'
) NOT NULL;