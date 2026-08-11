-- V2.0 queue upgrade migration for PostgreSQL
-- Recreates the queues table with the Queue 2.0 schema (INT type column replacing ENUM, added routing_key).
-- Only executed when upgrading from Flyway; skipped on fresh installations (handled by the 0-init-queue script).
-- The queue is transient: in-flight messages are lost on restart and replayed from executions state.

DROP TABLE IF EXISTS queues;
DROP TYPE  IF EXISTS queue_type;

CREATE TABLE IF NOT EXISTS queues (
    "offset"      SERIAL       PRIMARY KEY,
    type          INT          NOT NULL,
    "routing_key" VARCHAR(250),
    key           VARCHAR(250) NOT NULL,
    value         JSONB        NOT NULL,
    created       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues (type, "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset      ON queues (type, "offset");
CREATE INDEX IF NOT EXISTS queues_created           ON queues (created);
