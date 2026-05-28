DROP INDEX IF EXISTS queues_type__offset;
DROP INDEX IF EXISTS queues_created;

CREATE INDEX IF NOT EXISTS queues_created__type ON queues ("created", "type");

ALTER TABLE queues ALTER COLUMN "type" TYPE VARCHAR(250);