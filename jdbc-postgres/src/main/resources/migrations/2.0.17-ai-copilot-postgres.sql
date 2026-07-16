CREATE TABLE IF NOT EXISTS agent_thread (
    "key"       VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"     JSONB NOT NULL,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenant') STORED,
    "deleted"   BOOLEAN NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOLEAN)) STORED,
    "created"   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated"   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS agent_thread__tenant_deleted ON agent_thread ("tenant_id", "deleted");

CREATE OR REPLACE TRIGGER agent_thread_updated BEFORE UPDATE
    ON agent_thread FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();

CREATE TABLE IF NOT EXISTS agent_message (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      JSONB NOT NULL,
    "thread_id"  VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'threadId') STORED,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS agent_message__thread ON agent_message ("thread_id", "key");
