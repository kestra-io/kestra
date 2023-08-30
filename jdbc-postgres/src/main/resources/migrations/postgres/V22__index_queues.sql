-- Recreate the queues_type__* indexes by adding the offset column otherwise the index is not used as we order on offset.
-- Also make them partial to lower the index size.
DROP INDEX queues_type__consumer_flow_topology;
DROP INDEX queues_type__consumer_indexer;
DROP INDEX queues_type__consumer_executor;
DROP INDEX queues_type__consumer_worker;
DROP INDEX queues_type__consumer_scheduler;

CREATE INDEX queues_type__consumer_flow_topology ON queues (type, consumer_flow_topology, "offset") WHERE consumer_flow_topology = false;
CREATE INDEX queues_type__consumer_indexer ON queues (type, consumer_indexer, "offset") WHERE consumer_indexer = false;
CREATE INDEX queues_type__consumer_executor ON queues (type, consumer_executor, "offset") WHERE consumer_executor = false;
CREATE INDEX queues_type__consumer_worker ON queues (type, consumer_worker, "offset") WHERE consumer_worker = false;
CREATE INDEX queues_type__consumer_scheduler ON queues (type, consumer_scheduler, "offset") WHERE consumer_scheduler = false;

-- Go back to the original PK and queues_offset__type as they are useful for offset based poll and updates
DO $$
    BEGIN
        IF NOT exists (select constraint_name from information_schema.table_constraints where table_name = 'queues' and constraint_type = 'PRIMARY KEY') then
            ALTER TABLE queues ADD PRIMARY KEY("offset");
        END IF;
    END;
$$;
DROP INDEX IF EXISTS queues_offset;
CREATE INDEX IF NOT EXISTS queues_type__offset ON queues (type, "offset");