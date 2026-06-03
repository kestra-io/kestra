-- V2.0 queue upgrade migration for H2
-- Recreates the queues table with the Queue 2.0 schema (INT type column replacing ENUM, added routing_key).
-- Only executed when upgrading from Flyway; skipped on fresh installations (handled by the 0-init-queue script).
-- The queue is transient: in-flight messages are lost on restart and replayed from executions state.

DROP TABLE IF EXISTS queues;

CREATE TABLE IF NOT EXISTS queues (
    "offset"      BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type"        INT       NOT NULL,
    "routing_key" VARCHAR(250),
    "key"         VARCHAR(250) NOT NULL,
    "value"       TEXT         NOT NULL,
    "created"     TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset      ON queues ("type", "offset");
CREATE INDEX IF NOT EXISTS queues_created           ON queues ("created");
