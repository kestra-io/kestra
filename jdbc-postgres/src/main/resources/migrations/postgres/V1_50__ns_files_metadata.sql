CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'path') STORED,
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (value ->> 'parentPath') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'size' AS BIGINT)) STORED,
    "created" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'created')) STORED,
    "updated" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updated')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (FULLTEXT_INDEX(CAST(value ->> 'path' AS varchar))) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");

CREATE OR REPLACE TRIGGER namespace_file_metadata_updated BEFORE UPDATE
    ON namespace_file_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
