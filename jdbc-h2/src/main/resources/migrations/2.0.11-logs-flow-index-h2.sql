-- Drop the redundant logs_execution_id index: it is a strict leftmost prefix of
-- logs_execution_id__task_id (execution_id, task_id) and provides no extra coverage.
DROP INDEX IF EXISTS logs_execution_id;

-- Drop the redundant logs_timestamp index: every timestamp-range query on logs always carries a
-- tenant_id predicate, so logs_tenant_timestamp (tenant_id, timestamp, level) covers it fully.
DROP INDEX IF EXISTS logs_timestamp;

-- Add a composite index for the flow-scoped logs query (tenant + namespace + flow_id + timestamp
-- range). Fixes full-scan on large log datasets when filtering by namespace, flow_id and timestamp.
CREATE INDEX IF NOT EXISTS logs_tenant_namespace_flow_id_timestamp ON logs ("tenant_id", "namespace", "flow_id", "timestamp", "level");
