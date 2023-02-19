DO $$
    BEGIN
        BEGIN
            ALTER TABLE queues ADD consumer_flow_topology BOOLEAN DEFAULT FALSE;
        EXCEPTION
            WHEN duplicate_column THEN RAISE NOTICE 'consumer_flow_topology already exists in <table_name>.';
        END;
    END;
$$;

CREATE INDEX IF NOT EXISTS queues_type__consumer_flow_topology
    ON queues (type, consumer_flow_topology);

CREATE TABLE IF NOT EXISTS flow_topologies (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    source_namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{source, namespace}') STORED,
    source_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{source, id}') STORED,
    relation VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'relation') STORED,
    destination_namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{destination, namespace}') STORED,
    destination_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{destination, id}') STORED
);

CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies (destination_namespace, destination_id);
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies (destination_namespace, destination_id, source_namespace, source_id);