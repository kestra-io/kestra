CREATE OR REPLACE FUNCTION FULLTEXT_REPLACE(text, text) RETURNS text
AS 'SELECT REGEXP_REPLACE(COALESCE($1, ''''), ''[^a-zA-Z\d:]'', $2, ''g'');'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;

CREATE OR REPLACE FUNCTION FULLTEXT_INDEX(text) RETURNS tsvector
AS 'SELECT TO_TSVECTOR(''simple'', FULLTEXT_REPLACE($1, '' '')) || TO_TSVECTOR(''simple'', $1);'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;

CREATE OR REPLACE FUNCTION FULLTEXT_SEARCH(text) RETURNS tsquery
AS 'SELECT TO_TSQUERY(''simple'', FULLTEXT_REPLACE($1, '':* & '') || '':*'');'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;


CREATE TYPE ${prefix}queue_consumers AS ENUM (
    'indexer',
    'executor',
    'worker'
);

CREATE TYPE ${prefix}queue_type AS ENUM (
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

CREATE TABLE ${prefix}queues (
    "offset" SERIAL PRIMARY KEY,
    type ${prefix}queue_type NOT NULL,
    key VARCHAR(250) NOT NULL,
    value JSONB NOT NULL,
    consumers ${prefix}queue_consumers[]
);

CREATE INDEX ${prefix}queues_key ON ${prefix}queues (type, key);
CREATE INDEX ${prefix}queues_consumers ON ${prefix}queues (type, consumers);


CREATE TABLE ${prefix}flows (
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

CREATE INDEX ${prefix}flows_id ON ${prefix}flows (id);
CREATE INDEX ${prefix}flows_namespace ON ${prefix}flows (namespace);
CREATE INDEX ${prefix}flows_revision ON ${prefix}flows (revision);
CREATE INDEX ${prefix}flows_deleted ON ${prefix}flows (deleted);
CREATE INDEX ${prefix}flows_fulltext ON ${prefix}flows USING GIN (fulltext);
CREATE INDEX ${prefix}flows_source_code ON ${prefix}flows USING GIN (FULLTEXT_INDEX(source_code));


CREATE TABLE ${prefix}templates (
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

CREATE INDEX ${prefix}templates_namespace ON ${prefix}flows (namespace);
CREATE INDEX ${prefix}templates_revision ON ${prefix}flows (revision);
CREATE INDEX ${prefix}templates_deleted ON ${prefix}flows (deleted);
CREATE INDEX ${prefix}templates_fulltext ON ${prefix}templates USING GIN (fulltext);
