-- Fix the queue cleaner's delete index on PostgreSQL: the cleaner deletes per queue type
-- (WHERE created <= ? AND type = ?), so (type, created) lets it seek directly to that type's
-- rows instead of scanning the full created<=threshold range once per type with (created, type).

DROP INDEX IF EXISTS queues_created__type;
CREATE INDEX IF NOT EXISTS queues_type__created ON queues ("type", "created");
