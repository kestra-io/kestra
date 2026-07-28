-- AbstractJdbcLeaseStore filters lease rows via the shared buildTenantCondition(tenantId), which
-- expects a tenant_id column on every tenant-scoped table. Existing rows (server-mutex Locks have
-- none) leave it NULL, matching buildTenantCondition's null-tenant branch.
--
-- MySQL does not support IF NOT EXISTS on ALTER TABLE ADD COLUMN, so we guard it with an
-- information_schema check to keep the migration idempotent (safe on partial-failure recovery and
-- force-rerun), matching the other MySQL migrations.
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'locks' AND column_name = 'tenant_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE `locks` ADD COLUMN `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> ''$.tenantId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
