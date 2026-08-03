-- 2.0.21: merge executions_namespace (deleted, tenant_id, namespace) and executions_flow_id
-- (deleted, tenant_id, flow_id) into a single composite index. A flow always belongs to a
-- namespace, so (deleted, tenant_id, namespace, flow_id) still serves namespace-only queries
-- through its leftmost prefix while giving namespace + flow_id queries a full four-column seek,
-- and it removes one index from the hottest write table.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'executions_namespace__flow_id' AND NOT i.indisvalid
    ) THEN
        EXECUTE 'DROP INDEX executions_namespace__flow_id';
    END IF;
END $$;

CREATE INDEX CONCURRENTLY IF NOT EXISTS executions_namespace__flow_id ON executions ("deleted", "tenant_id", "namespace", "flow_id");

DROP INDEX CONCURRENTLY IF EXISTS executions_namespace;
DROP INDEX CONCURRENTLY IF EXISTS executions_flow_id;
