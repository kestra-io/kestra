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
    'io.kestra.ee.models.audits.AuditLog',
    'io.kestra.core.models.executions.MetricEntry',
    'io.kestra.core.runners.WorkerTrigger',
    'io.kestra.core.runners.WorkerTriggerResult'
) NOT NULL;

-- trigger logs have no execution id
alter table logs alter column "execution_id" set null;