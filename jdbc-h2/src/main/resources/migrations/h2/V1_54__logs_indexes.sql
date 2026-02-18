DROP INDEX IF EXISTS logs_namespace_flow;
CREATE INDEX IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");
