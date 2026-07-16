CREATE TABLE IF NOT EXISTS agent_thread (
    "key"       VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"     TEXT NOT NULL,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenant')),
    "deleted"   BOOLEAN NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "created"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS agent_thread__tenant_deleted ON agent_thread ("tenant_id", "deleted");

CREATE TABLE IF NOT EXISTS agent_message (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      TEXT NOT NULL,
    "thread_id"  VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.threadId')),
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS agent_message__thread ON agent_message ("thread_id", "key");
