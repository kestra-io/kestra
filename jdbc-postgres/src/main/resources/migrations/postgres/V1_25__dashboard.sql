CREATE TABLE IF NOT EXISTS dashboards (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    id VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    title VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'title') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value->>'title' AS VARCHAR))
    ) STORED,
    source_code TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS dashboards_tenant ON dashboards ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS dashboards_fulltext ON dashboards USING GIN (fulltext);

CREATE OR REPLACE TRIGGER dashboard_updated BEFORE UPDATE
    ON dashboards FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();