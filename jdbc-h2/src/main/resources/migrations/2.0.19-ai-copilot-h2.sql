CREATE TABLE IF NOT EXISTS ai_agent_thread (
    "key"       VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"     TEXT NOT NULL,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenant')),
    "deleted"   BOOLEAN NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "created"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ai_agent_thread__tenant_deleted ON ai_agent_thread ("tenant_id", "deleted");

CREATE TABLE IF NOT EXISTS ai_agent_message (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      TEXT NOT NULL,
    "tenant_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenant')),
    "thread_id"  VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.threadId')),
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ai_agent_message__tenant_thread ON ai_agent_message ("tenant_id", "thread_id", "key");
