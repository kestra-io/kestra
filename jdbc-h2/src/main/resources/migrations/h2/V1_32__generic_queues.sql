CREATE TABLE IF NOT EXISTS generic_queues (
    "offset" INT AUTO_INCREMENT PRIMARY KEY,
    "topic" VARCHAR(250) NOT NULL,
    "namespace" VARCHAR(250) NOT NULL,
    "tenant" VARCHAR(250),
    "value" TEXT NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS generic_queues_topic_namespace_tenant_offset ON generic_queues ("topic", "namespace", "tenant", "offset");