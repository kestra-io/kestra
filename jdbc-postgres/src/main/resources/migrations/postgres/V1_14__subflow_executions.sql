ALTER TABLE IF EXISTS workertaskexecutions RENAME TO subflow_executions;

ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.runners.SubflowExecutionResult';