CREATE TABLE IF NOT EXISTS generic_queues (
    "offset" SERIAL PRIMARY KEY,
    topic VARCHAR(250) NOT NULL,
    namespace VARCHAR(250) NOT NULL,
    tenant VARCHAR(250) NOT NULL,
    value JSONB NOT NULL,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS generic_queues_topic__namespace__tenant__offset ON generic_queues (topic, namespace, tenant, "offset");