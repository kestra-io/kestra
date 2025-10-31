CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "expiration_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.expirationDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSSSSS''Z''')),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name'))
    );

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");
