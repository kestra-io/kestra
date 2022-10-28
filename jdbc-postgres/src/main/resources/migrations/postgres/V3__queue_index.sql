ALTER TABLE queues
    ADD consumer_indexer BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD consumer_executor BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD consumer_worker BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD consumer_scheduler BOOLEAN DEFAULT FALSE;

CREATE INDEX queues_type__consumer_indexer
    ON queues (type, consumer_indexer);

CREATE INDEX queues_type__consumer_executor
    ON queues (type, consumer_executor);

CREATE INDEX queues_type__consumer_worker
    ON queues (type, consumer_worker);

CREATE INDEX queues_type__consumer_scheduler
    ON queues (type, consumer_scheduler);

UPDATE queues
SET
    consumer_indexer = consumers IS NOT NULL AND "consumers" && '{indexer}'::queue_consumers[],
    consumer_executor = consumers IS NOT NULL AND "consumers" && '{executor}'::queue_consumers[],
    consumer_worker = consumers IS NOT NULL AND "consumers" && '{worker}'::queue_consumers[],
    consumer_scheduler = consumers IS NOT NULL AND "consumers" && '{scheduler}'::queue_consumers[];

ALTER TABLE queues
    DROP COLUMN consumers;


DROP TYPE queue_consumers;