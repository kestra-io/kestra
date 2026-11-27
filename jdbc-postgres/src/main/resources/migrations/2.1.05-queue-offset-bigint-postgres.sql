-- Widen the PostgreSQL queue offset sequence and column to 64-bit BIGINT (Queue 2.0).
-- In PostgreSQL, SERIAL creates an integer sequence capped at 2,147,483,647.
-- Widening the sequence to BIGINT and the column to BIGINT ensures the queue does not
-- overflow under high throughput.
ALTER SEQUENCE IF EXISTS queues_offset_seq AS BIGINT;
ALTER TABLE queues ALTER COLUMN "offset" TYPE BIGINT;
