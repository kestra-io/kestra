DROP INDEX `ix_type__offset` ON queues;
DROP INDEX `ix_created` ON queues;

CREATE INDEX ix_created__type ON queues (`created`, `type`);