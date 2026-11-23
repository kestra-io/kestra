-- Queue 2.0 upgrade for H2. Builds the final Queue 2.0 shape directly and converges any
-- pre-existing shape to it: a legacy table has already been dropped by
-- 2.0.02-queue-drop-legacy-h2.sql before this runs (so CREATE below establishes it fresh); a table
-- from baseline-queue-h2.sql (fresh install, "type" still INT) or an already-migrated 2.0.0-rcN
-- table (already final) both converge here without ever losing a row.

CREATE TABLE IF NOT EXISTS queues (
    "offset"      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type"        VARCHAR(250) NOT NULL,
    "routing_key" VARCHAR(250),
    "key"         VARCHAR(250) NOT NULL,
    "value"       TEXT         NOT NULL,
    "created"     TIMESTAMP    NOT NULL
);

-- Converge a fresh baseline table (type INT) to the final width; a no-op once already VARCHAR.
ALTER TABLE queues ALTER COLUMN "type" TYPE VARCHAR(250);

CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__created ON queues ("type", "created");

DROP INDEX IF EXISTS queues_type__offset ON queues;
DROP INDEX IF EXISTS queues_created ON queues;
DROP INDEX IF EXISTS queues_created__type ON queues;
