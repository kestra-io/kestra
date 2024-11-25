DROP INDEX execution_queued__flow;
CREATE INDEX IF NOT EXISTS execution_queued__flow_date ON execution_queued ("tenant_id", "namespace", "flow_id", "date" );