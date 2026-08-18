-- New table: execution indexed fields
CREATE TABLE IF NOT EXISTS execution_indexed_fields (
    "key"          VARCHAR(250) NOT NULL PRIMARY KEY,
    "tenant_id"    VARCHAR(150),
    "execution_id" VARCHAR(150) NOT NULL,
    "field_key"    VARCHAR(128) NOT NULL,
    "field_value"  TEXT,
    "namespace"    VARCHAR(150),
    "flow_id"      VARCHAR(150),
    "value"        TEXT
);

CREATE INDEX IF NOT EXISTS execution_indexed_fields_execution_id ON execution_indexed_fields ("execution_id");
CREATE INDEX IF NOT EXISTS execution_indexed_fields_tenant_key_value ON execution_indexed_fields ("tenant_id", "field_key", "field_value");
CREATE INDEX IF NOT EXISTS execution_indexed_fields_tenant_ns_flow ON execution_indexed_fields ("tenant_id", "namespace", "flow_id");
