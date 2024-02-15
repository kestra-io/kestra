/* ----------------------- service_instance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance
(
    key             VARCHAR(250) NOT NULL PRIMARY KEY,
    value           JSONB        NOT NULL,
    service_id      VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    service_type    VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'type') STORED,
    state           VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'state') STORED,
    created_at      TIMESTAMPTZ  NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'createdAt')) STORED,
    updated_at      TIMESTAMPTZ  NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updatedAt')) STORED
);
