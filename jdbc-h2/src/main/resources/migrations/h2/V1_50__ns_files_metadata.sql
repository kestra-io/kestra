CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path')),
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (JQ_STRING("value", '.parentPath')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.size')),
    "created" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.created'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updated'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path'))
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");
