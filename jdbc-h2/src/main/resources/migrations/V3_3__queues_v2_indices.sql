DROP INDEX IF EXISTS queues_type__offset ON queues;
DROP INDEX IF EXISTS queues_created ON queues;

CREATE INDEX IF NOT EXISTS queues_created__type ON queues ("created", "type");

