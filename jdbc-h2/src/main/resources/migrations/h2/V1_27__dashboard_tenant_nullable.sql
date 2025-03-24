DROP INDEX IF EXISTS dashboards_tenant;
DROP INDEX IF EXISTS dashboards_id;

ALTER TABLE dashboards DROP COLUMN "tenant_id";
ALTER TABLE dashboards ADD COLUMN "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));

CREATE INDEX IF NOT EXISTS dashboards_tenant ON dashboards ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS dashboards_id ON dashboards ("id", "deleted", "tenant_id");