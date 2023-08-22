DO $$
    BEGIN
        BEGIN
            CREATE TYPE state_type AS ENUM (
                'CREATED',
                'RUNNING',
                'PAUSED',
                'RESTARTED',
                'KILLING',
                'SUCCESS',
                'WARNING',
                'FAILED',
                'KILLED'
                );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END;
    END;
$$;

DO $$
    BEGIN
        BEGIN
            CREATE TYPE log_level AS ENUM (
                'ERROR',
                'WARN',
                'INFO',
                'DEBUG',
                'TRACE'
                );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END;
    END;
$$;

DO $$
    BEGIN
        BEGIN
            CREATE TYPE queue_consumers AS ENUM (
                'indexer',
                'executor',
                'worker',
                'scheduler'
                );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END;
    END;
$$;

DO $$
    BEGIN
        BEGIN
            CREATE TYPE queue_type AS ENUM (
                'io.kestra.core.models.executions.Execution',
                'io.kestra.core.models.flows.Flow',
                'io.kestra.core.models.templates.Template',
                'io.kestra.core.models.executions.ExecutionKilled',
                'io.kestra.core.runners.WorkerTask',
                'io.kestra.core.runners.WorkerTaskResult',
                'io.kestra.core.runners.WorkerInstance',
                'io.kestra.core.runners.WorkerTaskRunning',
                'io.kestra.core.models.executions.LogEntry',
                'io.kestra.core.models.triggers.Trigger'
                );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END;
    END;
$$;

CREATE OR REPLACE FUNCTION FULLTEXT_REPLACE(text, text) RETURNS text
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
    RETURN TRIM(BOTH $2 FROM REGEXP_REPLACE(COALESCE($1, ''), '[^a-zA-Z\d:]', $2, 'g'));

CREATE OR REPLACE FUNCTION FULLTEXT_INDEX(text) RETURNS tsvector
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
    RETURN TO_TSVECTOR('simple', FULLTEXT_REPLACE($1, ' ')) || TO_TSVECTOR('simple', $1);

CREATE OR REPLACE FUNCTION FULLTEXT_SEARCH(text) RETURNS tsquery
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
    RETURN CASE WHEN FULLTEXT_REPLACE($1, '') = '' THEN TO_TSQUERY('')
        ELSE TO_TSQUERY('simple', FULLTEXT_REPLACE($1, ':* & ') || ':*')
    END;

CREATE OR REPLACE FUNCTION STATE_FROMTEXT(text) RETURNS state_type
    LANGUAGE SQL
    IMMUTABLE
    RETURN CAST($1 AS state_type);

CREATE OR REPLACE FUNCTION LOGLEVEL_FROMTEXT(text) RETURNS log_level
    LANGUAGE SQL
    IMMUTABLE
    RETURN CAST($1 AS log_level);

CREATE OR REPLACE FUNCTION PARSE_ISO8601_DATETIME(text) RETURNS timestamptz
    LANGUAGE SQL
    IMMUTABLE
    RETURN $1::timestamptz;

CREATE OR REPLACE FUNCTION PARSE_ISO8601_TIMESTAMP(text) RETURNS int
    LANGUAGE SQL
    IMMUTABLE
    RETURN EXTRACT(epoch FROM $1::timestamptz AT TIME ZONE 'utc');

CREATE OR REPLACE FUNCTION PARSE_ISO8601_DURATION(text) RETURNS interval
    LANGUAGE SQL
    IMMUTABLE
    RETURN $1::interval;;

CREATE OR REPLACE FUNCTION UPDATE_UPDATED_DATETIME() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated = now();
    RETURN NEW;
END;
$$ language 'plpgsql';


/* ----------------------- queues ----------------------- */
CREATE TABLE IF NOT EXISTS queues (
    "offset" SERIAL PRIMARY KEY,
    type queue_type NOT NULL,
    key VARCHAR(250) NOT NULL,
    value JSONB NOT NULL,
    consumers queue_consumers[],
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS queues_type__offset ON queues (type, "offset");
CREATE INDEX IF NOT EXISTS queues_updated ON queues ("updated");

CREATE OR REPLACE TRIGGER queues_updated BEFORE UPDATE
    ON queues FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* ----------------------- flows ----------------------- */
CREATE TABLE IF NOT EXISTS flows (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    id VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    revision INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'revision' AS INT)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value->>'namespace' AS VARCHAR)) ||
        FULLTEXT_INDEX(CAST(value->>'id' AS VARCHAR))
    ) STORED,
    source_code TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS flows_namespace ON flows (deleted, namespace);
CREATE INDEX IF NOT EXISTS flows_namespace__id__revision ON flows (deleted, namespace, id, revision);
CREATE INDEX IF NOT EXISTS flows_fulltext ON flows USING GIN (fulltext);
CREATE INDEX IF NOT EXISTS flows_source_code ON flows USING GIN (FULLTEXT_INDEX(source_code));


/* ----------------------- templates ----------------------- */
CREATE TABLE IF NOT EXISTS templates (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    id VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (FULLTEXT_INDEX(
        FULLTEXT_REPLACE(CAST(value->>'namespace' AS VARCHAR), ' ') || ' ' ||
        FULLTEXT_REPLACE(CAST(value->>'id' AS VARCHAR), ' ')
    )) STORED
);

CREATE INDEX IF NOT EXISTS templates_namespace ON templates (deleted, namespace);
CREATE INDEX IF NOT EXISTS templates_namespace__id ON templates (deleted, namespace, id);
CREATE INDEX IF NOT EXISTS templates_fulltext ON templates USING GIN (fulltext);


/* ----------------------- executions ----------------------- */
CREATE TABLE IF NOT EXISTS executions (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS bool)) STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    state_current state_type NOT NULL GENERATED ALWAYS AS (STATE_FROMTEXT(value #>> '{state, current}')) STORED,
    state_duration BIGINT NOT NULL GENERATED ALWAYS AS (EXTRACT(MILLISECONDS FROM PARSE_ISO8601_DURATION(value #>> '{state, duration}'))) STORED,
    start_date TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value #>> '{state, startDate}')) STORED,
    end_date TIMESTAMP GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value #>> '{state, endDate}')) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'id' AS varchar))
    ) STORED
);

CREATE INDEX IF NOT EXISTS executions_namespace ON executions (deleted, namespace);
CREATE INDEX IF NOT EXISTS executions_flow_id ON executions (deleted, flow_id);
CREATE INDEX IF NOT EXISTS executions_state_current ON executions (deleted, state_current);
CREATE INDEX IF NOT EXISTS executions_start_date ON executions (deleted, start_date);
CREATE INDEX IF NOT EXISTS executions_end_date ON executions (deleted, end_date);
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions (deleted, state_duration);
CREATE INDEX IF NOT EXISTS executions_fulltext ON executions USING GIN (fulltext);


/* ----------------------- triggers ----------------------- */
CREATE TABLE IF NOT EXISTS triggers (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    trigger_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'triggerId') STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED
);

CREATE INDEX IF NOT EXISTS triggers_execution_id ON triggers (execution_id);


/* ----------------------- logs ----------------------- */
CREATE TABLE IF NOT EXISTS logs (
    key VARCHAR(30) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS bool)) STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    task_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'taskId') STORED,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    taskrun_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'taskRunId') STORED,
    attempt_number INT GENERATED ALWAYS AS (CAST(value ->> 'attemptNumber' AS INTEGER)) STORED,
    trigger_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'triggerId') STORED,
    level log_level NOT NULL GENERATED ALWAYS AS (LOGLEVEL_FROMTEXT(value ->> 'level')) STORED,
    timestamp TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'timestamp')) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskId' AS varchar), '')) ||
        FULLTEXT_INDEX(CAST(value ->> 'executionId' AS varchar)) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskRunId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'triggerId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'message' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'thread' AS varchar), ''))
    ) STORED
);

CREATE INDEX IF NOT EXISTS logs_execution_id ON logs (deleted, execution_id);
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs (deleted, execution_id, task_id);
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs (deleted, execution_id, taskrun_id);
CREATE INDEX IF NOT EXISTS logs_fulltext ON logs USING GIN (fulltext);


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE IF NOT EXISTS multipleconditions (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    condition_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'conditionId') STORED,
    start_date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'start')) STORED,
    end_date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'end')) STORED
);

CREATE INDEX IF NOT EXISTS multipleconditions_namespace__flow_id__condition_id ON multipleconditions (namespace, flow_id, condition_id);
CREATE INDEX IF NOT EXISTS multipleconditions_start_date__end_date ON multipleconditions (start_date, end_date);


/* ----------------------- workertaskexecutions ----------------------- */
CREATE TABLE IF NOT EXISTS workertaskexecutions (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executorstate (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executordelayed (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED
);

CREATE INDEX IF NOT EXISTS executordelayed_date ON executordelayed (date);

/* ---!!! previously on V2__setting.sql !!!--- */
CREATE TABLE IF NOT EXISTS settings (
                                        key VARCHAR(250) NOT NULL PRIMARY KEY,
                                        value JSONB NOT NULL
);

/* ---!!! previously on V3__queue_index.sql !!!--- */
ALTER TABLE queues
    ADD IF NOT EXISTS consumer_indexer BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD IF NOT EXISTS consumer_executor BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD IF NOT EXISTS consumer_worker BOOLEAN DEFAULT FALSE;

ALTER TABLE queues
    ADD IF NOT EXISTS consumer_scheduler BOOLEAN DEFAULT FALSE;

DO $$ BEGIN
    UPDATE queues
    SET
        consumer_indexer = consumers IS NOT NULL AND "consumers" && '{indexer}'::queue_consumers[],
        consumer_executor = consumers IS NOT NULL AND "consumers" && '{executor}'::queue_consumers[],
        consumer_worker = consumers IS NOT NULL AND "consumers" && '{worker}'::queue_consumers[],
        consumer_scheduler = consumers IS NOT NULL AND "consumers" && '{scheduler}'::queue_consumers[];
EXCEPTION
    WHEN undefined_column THEN RAISE NOTICE 'column consumers does not exist';
END $$;

ALTER TABLE queues DROP COLUMN IF EXISTS consumers;

DROP TYPE IF EXISTS queue_consumers;

/* ---!!! previously on V5__flow_topologies.sql !!!--- */
DO $$
    BEGIN
        BEGIN
            ALTER TABLE queues ADD consumer_flow_topology BOOLEAN DEFAULT FALSE;
        EXCEPTION
            WHEN duplicate_column THEN RAISE NOTICE 'consumer_flow_topology already exists in <table_name>.';
        END;
    END;
$$;

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

/* ---!!! previously on V5__flow_topologies.sql !!!--- */
DO $$
    BEGIN
        BEGIN
            ALTER TABLE queues ADD consumer_flow_topology BOOLEAN DEFAULT FALSE;
        EXCEPTION
            WHEN duplicate_column THEN RAISE NOTICE 'consumer_flow_topology already exists in <table_name>.';
        END;
    END;
$$;

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

/* ---!!! previously on V7__missing_execution_id.sql !!!--- */
ALTER TABLE executions ADD COLUMN IF NOT EXISTS id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED;

/* ---!!! previously on V12__metrics.sql !!!--- */
ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.models.executions.MetricEntry';

/* ----------------------- metrics ----------------------- */
CREATE TABLE IF NOT EXISTS metrics (
   key VARCHAR(30) NOT NULL PRIMARY KEY,
   value JSONB NOT NULL,
   deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS bool)) STORED,
   namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
   flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
   task_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskId') STORED,
   execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
   taskrun_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskRunId') STORED,
   metric_name VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
   timestamp TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'timestamp')) STORED
);

ALTER TABLE metrics ADD COLUMN IF NOT EXISTS metric_value FLOAT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'value' AS FLOAT)) STORED;

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;
CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics (deleted, namespace, flow_id);
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics (deleted, execution_id);
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics (deleted, timestamp);

/* ---!!! previously on V14__queues_consumer_group.sql !!!--- */
ALTER TABLE queues ADD COLUMN IF NOT EXISTS consumer_group VARCHAR(250);

/* ---!!! previously on V17__polling_trigger.sql !!!--- */
ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.runners.WorkerTriggerResult';
DO $$
    BEGIN
        BEGIN
            ALTER TYPE queue_type RENAME VALUE 'io.kestra.core.runners.WorkerTask' TO 'io.kestra.core.runners.WorkerJob';
        EXCEPTION
            WHEN invalid_parameter_value THEN null;
        END;
    END;
$$;

-- trigger logs have no execution id
alter table logs alter column execution_id drop not null;

/* ---!!! previously on V18__trigger_fulltext_col.sql !!!--- */
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS fulltext TSVECTOR GENERATED ALWAYS AS (
                FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
                FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
                FULLTEXT_INDEX(CAST(value ->> 'triggerId' AS varchar)) ||
                FULLTEXT_INDEX(COALESCE(CAST(value ->> 'executionId' AS varchar), ''))
    ) STORED;

/* ---!!! previously on V19__index_execution-labels.sql !!!--- */
create index IF NOT EXISTS executions_labels ON executions USING GIN((value -> 'labels'));

/* ---!!! previously on V20__index_flow_labels.sql !!!--- */
create index IF NOT EXISTS flows_labels ON flows USING GIN((value -> 'labels'));

/* ---!!! previously on V21__index_logs.sql !!!--- */
DROP INDEX IF EXISTS logs_namespace;
DROP INDEX IF EXISTS logs_timestamp;
CREATE INDEX IF NOT EXISTS logs_namespace_flow ON logs (deleted, timestamp, level, namespace, flow_id);

/* ---!!! previously on V22__index_queues.sql !!!--- */
-- Recreate the queues_type__* indexes by adding the offset column otherwise the index is not used as we order on offset.
-- Also make them partial to lower the index size.
DROP INDEX IF EXISTS queues_type__consumer_flow_topology;
DROP INDEX IF EXISTS queues_type__consumer_indexer;
DROP INDEX IF EXISTS queues_type__consumer_executor;
DROP INDEX IF EXISTS queues_type__consumer_worker;
DROP INDEX IF EXISTS queues_type__consumer_scheduler;

CREATE INDEX IF NOT EXISTS queues_type__consumer_flow_topology ON queues (type, consumer_flow_topology, "offset") WHERE consumer_flow_topology = false;
CREATE INDEX IF NOT EXISTS queues_type__consumer_indexer ON queues (type, consumer_indexer, "offset") WHERE consumer_indexer = false;
CREATE INDEX IF NOT EXISTS queues_type__consumer_executor ON queues (type, consumer_executor, "offset") WHERE consumer_executor = false;
CREATE INDEX IF NOT EXISTS queues_type__consumer_worker ON queues (type, consumer_worker, "offset") WHERE consumer_worker = false;
CREATE INDEX IF NOT EXISTS queues_type__consumer_scheduler ON queues (type, consumer_scheduler, "offset") WHERE consumer_scheduler = false;

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