-- Fix the queue cleaner's delete index on MySQL: the cleaner deletes per queue type
-- (WHERE created <= ? AND type = ?), so (type, created) lets it seek directly to that type's
-- rows instead of scanning the full created<=threshold range once per type with (created, type).

DROP INDEX `ix_created__type` ON queues;
CREATE INDEX `ix_type__created` ON queues (`type`, `created`);
