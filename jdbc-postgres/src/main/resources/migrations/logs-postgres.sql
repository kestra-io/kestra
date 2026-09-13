/* ----------------------- logs (external log store) ----------------------- */
/*
 * Standalone, idempotent DDL for the PostgreSQL log store, run against the log datasource by
 * V2_0LogsMigration when `kestra.logs.type` is postgres. Mirrors the `logs` table + final index set
 * from baseline-postgres.sql (+ 2.0.11), with the table name templated via ${table}. Prepends the
 * prerequisite type + functions (the log_level enum, LOGLEVEL_FROMTEXT, PARSE_ISO8601_DATETIME and
 * the FULLTEXT_* helpers) because a dedicated log database has not been through baseline-postgres.sql.
 * Idempotent (IF NOT EXISTS / CREATE OR REPLACE / duplicate_object guard). CONCURRENTLY is omitted:
 * a fresh/empty log table needs no online index build.
 */
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

CREATE OR REPLACE FUNCTION FULLTEXT_REPLACE(text, text) RETURNS text
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
RETURN TRIM(BOTH $2 FROM ARRAY_TO_STRING(
   ARRAY(
       SELECT DISTINCT *
       FROM UNNEST(REGEXP_SPLIT_TO_ARRAY(COALESCE($1, ''), '[^a-zA-Z\d]')) AS a
       WHERE a != ''
   ),
   $2
));

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

CREATE OR REPLACE FUNCTION LOGLEVEL_FROMTEXT(text) RETURNS log_level
    LANGUAGE SQL
    IMMUTABLE
    RETURN CAST($1 AS log_level);

CREATE OR REPLACE FUNCTION PARSE_ISO8601_DATETIME(text) RETURNS timestamptz
    LANGUAGE SQL
    IMMUTABLE
    RETURN $1::timestamptz;

CREATE TABLE IF NOT EXISTS ${table} (
    key VARCHAR(30) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    task_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'taskId') STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    taskrun_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'taskRunId') STORED,
    attempt_number INT GENERATED ALWAYS AS (CAST(value ->> 'attemptNumber' AS INTEGER)) STORED,
    trigger_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'triggerId') STORED,
    level log_level NOT NULL GENERATED ALWAYS AS (LOGLEVEL_FROMTEXT(value ->> 'level')) STORED,
    timestamp TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'timestamp')) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'executionId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskRunId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'triggerId' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'message' AS varchar), '')) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'thread' AS varchar), ''))
    ) STORED,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    execution_kind VARCHAR(32) GENERATED ALWAYS AS (value ->> 'executionKind') STORED
);

CREATE INDEX IF NOT EXISTS ${table}_execution_id__task_id ON ${table} (execution_id, task_id);
CREATE INDEX IF NOT EXISTS ${table}_execution_id__taskrun_id ON ${table} (execution_id, taskrun_id);
CREATE INDEX IF NOT EXISTS ${table}_fulltext ON ${table} USING GIN (fulltext);
CREATE INDEX IF NOT EXISTS ${table}_tenant_timestamp ON ${table} ("tenant_id", "timestamp", "level");
CREATE INDEX IF NOT EXISTS ${table}_tenant_namespace_timestamp ON ${table} ("tenant_id", "namespace", "timestamp", "level");
CREATE INDEX IF NOT EXISTS ${table}_tenant_namespace_flow_id_timestamp ON ${table} ("tenant_id", "namespace", "flow_id", "timestamp", "level");
