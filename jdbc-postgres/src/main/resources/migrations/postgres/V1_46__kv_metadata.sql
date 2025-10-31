CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    "description" TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "expiration_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'expirationDate')) STORED,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'name' AS varchar))
    ) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");

CREATE OR REPLACE TRIGGER kv_metadata_updated BEFORE UPDATE
    ON kv_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
