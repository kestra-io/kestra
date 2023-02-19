CREATE TABLE IF NOT EXISTS `flow_topologies` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `source_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.namespace') STORED NOT NULL,
    `source_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.id') STORED NOT NULL,
    `relation` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.relation') STORED NOT NULL,
    `destination_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.namespace') STORED NOT NULL,
    `destination_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.id') STORED NOT NULL,
    INDEX ix_destination (destination_namespace, destination_id),
    INDEX ix_destination__source (destination_namespace, destination_id, source_namespace, source_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;



ALTER TABLE queues MODIFY `consumers` ENUM(
    'indexer',
    'executor',
    'worker',
    'scheduler',
    'flow_topology'
);