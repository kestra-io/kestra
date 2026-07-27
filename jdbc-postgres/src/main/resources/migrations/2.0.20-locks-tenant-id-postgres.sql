-- AbstractJdbcLeaseStore filters lease rows via the shared buildTenantCondition(tenantId), which
-- expects a tenant_id column on every tenant-scoped table. Existing rows (server-mutex Locks have
-- none) leave it NULL, matching buildTenantCondition's null-tenant branch.
ALTER TABLE locks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED;
