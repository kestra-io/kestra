-- H2 queue baseline schema (Queue 2.0)
-- Creates the JDBC queue table used by the memory/H2 queue backend.

DROP TABLE IF EXISTS queues;
CREATE TABLE IF NOT EXISTS queues (
    "offset" BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type" INT NOT NULL,
    "routing_key" VARCHAR(250),
    "key" VARCHAR(250) NOT NULL,
    "value" TEXT NOT NULL,
    "created" TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset ON queues ("type", "offset");
CREATE INDEX IF NOT EXISTS queues_created ON queues ("created");
