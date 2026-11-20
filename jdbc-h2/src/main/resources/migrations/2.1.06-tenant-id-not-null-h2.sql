-- 2.1.06: Add NOT NULL constraint to tenant_id on all tenant-scoped tables.
-- Idempotent: In H2, ALTER TABLE IF EXISTS ... ALTER COLUMN SET NOT NULL is safe to re-run.

ALTER TABLE IF EXISTS flows ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS executions ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS triggers ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS logs ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS metrics ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS execution_queued ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS execution_statistics ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS multipleconditions ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS dashboards ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS concurrency_limit ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS namespace_file_metadata ALTER COLUMN "tenant_id" SET NOT NULL;
-- Note: `locks` (cluster-wide mutexes) and `notifications` / `notification_items` (user-global alerts)
-- intentionally allow NULL tenant_id and are not constrained.
ALTER TABLE IF EXISTS mcp ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS mcp_session ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS kv_metadata ALTER COLUMN "tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS flow_topologies ALTER COLUMN "source_tenant_id" SET NOT NULL;
ALTER TABLE IF EXISTS flow_topologies ALTER COLUMN "destination_tenant_id" SET NOT NULL;
