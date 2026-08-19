-- Queue 2.0 upgrade for PostgreSQL. Builds the final Queue 2.0 shape directly and converges any
-- pre-existing shape to it: a legacy table has already been dropped by
-- 2.0.02-queue-drop-legacy-postgres.sql before this runs (so CREATE below establishes it fresh); a
-- table from baseline-queue-postgres.sql (fresh install, "type" still INT) or an already-migrated
-- 2.0.0-rcN table (already final) both converge here without ever losing a row.

CREATE TABLE IF NOT EXISTS queues (
    "offset"      SERIAL       PRIMARY KEY,
    type          VARCHAR(250) NOT NULL,
    "routing_key" VARCHAR(250),
    key           VARCHAR(250) NOT NULL,
    value         JSONB        NOT NULL,
    created       TIMESTAMPTZ  NOT NULL
);

-- Converge a fresh baseline table (type INT) to the final width; a no-op once already VARCHAR.
ALTER TABLE queues ALTER COLUMN "type" TYPE VARCHAR(250);

CREATE INDEX IF NOT EXISTS queues_type__key__offset ON queues (type, "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queues_type__created ON queues (type, created);

DROP INDEX IF EXISTS queues_type__offset;
DROP INDEX IF EXISTS queues_created;
DROP INDEX IF EXISTS queues_created__type;
