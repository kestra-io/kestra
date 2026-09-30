CREATE TABLE IF NOT EXISTS notifications (
    key           VARCHAR(250) NOT NULL PRIMARY KEY,
    value         JSONB        NOT NULL,
    id            VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    user_id       VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'userId') STORED,
    tenant_id     VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    type          VARCHAR(50)  NOT NULL GENERATED ALWAYS AS (value ->> 'type') STORED,
    read          BOOL         NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'read' AS bool)) STORED,
    reference_id  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'referenceId') STORED,
    created_date  TIMESTAMP    NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'createdDate')) STORED,
    updated_date  TIMESTAMP    NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updatedDate')) STORED
);

CREATE INDEX IF NOT EXISTS notifications_user_updated ON notifications (user_id, updated_date);
CREATE INDEX IF NOT EXISTS notifications_user_created ON notifications (user_id, created_date);
CREATE INDEX IF NOT EXISTS notifications_user_read ON notifications (user_id, read);
CREATE INDEX IF NOT EXISTS notifications_user_type_reference ON notifications (user_id, type, reference_id);
