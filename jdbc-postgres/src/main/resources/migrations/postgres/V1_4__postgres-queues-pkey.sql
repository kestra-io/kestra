-- We drop the PK, otherwise its index is used by the poll query which is sub-optimal.
-- We create an hash index on offset that will be used instead when filtering on offset.
ALTER TABLE queues DROP CONSTRAINT IF EXISTS queues_pkey;

CREATE INDEX IF NOT EXISTS queues_offset ON queues USING hash ("offset");