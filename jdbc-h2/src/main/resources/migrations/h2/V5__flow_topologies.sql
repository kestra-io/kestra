CREATE TABLE IF NOT EXISTS flow_topologies (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "source_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.namespace')),
    "source_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.id')),
    "relation" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.relation')),
    "destination_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.namespace')),
    "destination_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.id'))
);

CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies ("destination_namespace", "destination_id");
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies ("destination_namespace", "destination_id", "source_namespace", "source_id");

ALTER TABLE queues
    ALTER COLUMN "consumers" ENUM(
        'indexer',
        'executor',
        'worker',
        'scheduler',
        'flow_topology'
        ) ARRAY;