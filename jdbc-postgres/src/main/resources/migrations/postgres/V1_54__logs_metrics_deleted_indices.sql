CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_execution_id ON logs (execution_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_execution_id__task_id ON logs (execution_id, task_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_execution_id__taskrun_id ON logs (execution_id, taskrun_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_namespace_flow ON logs (tenant_id, timestamp, level, namespace, flow_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_flow_id ON metrics (tenant_id, namespace, flow_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_execution_id ON metrics (execution_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_timestamp ON metrics (tenant_id, timestamp);