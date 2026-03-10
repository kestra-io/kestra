DROP INDEX CONCURRENTLY IF EXISTS logs_namespace_flow;
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");
