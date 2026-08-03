-- 2.0.21: merge executions_namespace (deleted, tenant_id, namespace) and executions_flow_id
-- (deleted, tenant_id, flow_id) into a single composite index. A flow always belongs to a
-- namespace, so (deleted, tenant_id, namespace, flow_id) still serves namespace-only queries
-- through its leftmost prefix while giving namespace + flow_id queries a full four-column seek,
-- and it removes one index from the hottest write table.
CREATE INDEX IF NOT EXISTS executions_namespace__flow_id ON executions ("deleted", "tenant_id", "namespace", "flow_id");

DROP INDEX IF EXISTS executions_namespace;
DROP INDEX IF EXISTS executions_flow_id;
