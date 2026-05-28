-- OSS PostgreSQL baseline schema — represents the Kestra 1.3 (Flyway-era) database.
-- The 2.0 upgrade script (upgrade-v2.0-postgres.sql) bridges the gap from this schema to 2.0.

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
                'KILLED',
                'CANCELLED',
                'QUEUED',
                'RETRYING',
                'RETRIED',
                'SKIPPED',
                'BREAKPOINT',
                'SUBMITTED',
                'RESUBMITTED'
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

-- queue_consumers and queue_type enums removed (queue table uses INT type since Queue 2.0)

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


/* queues table is created by the separate queue baseline (baseline-queue-postgres.sql) */


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
    source_code TEXT NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    updated VARCHAR(250) GENERATED ALWAYS AS (value ->> 'updated') STORED
);

CREATE INDEX IF NOT EXISTS flows_namespace ON flows (deleted, tenant_id, namespace);
CREATE INDEX IF NOT EXISTS flows_namespace__id__revision ON flows (deleted, tenant_id, namespace, id, revision);
CREATE INDEX IF NOT EXISTS flows_fulltext ON flows USING GIN (fulltext);
CREATE INDEX IF NOT EXISTS flows_source_code ON flows USING GIN (FULLTEXT_INDEX(source_code));
CREATE INDEX IF NOT EXISTS flows_labels ON flows USING GIN ((value -> 'labels'));


/* ----------------------- executions ----------------------- */
CREATE TABLE IF NOT EXISTS executions (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS bool)) STORED,
    id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    state_current state_type NOT NULL GENERATED ALWAYS AS (STATE_FROMTEXT(value #>> '{state, current}')) STORED,
    state_duration BIGINT GENERATED ALWAYS AS (EXTRACT(MILLISECONDS FROM PARSE_ISO8601_DURATION(value #>> '{state, duration}'))) STORED,
    start_date TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value #>> '{state, startDate}')) STORED,
    end_date TIMESTAMP GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value #>> '{state, endDate}')) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'id' AS varchar))
    ) STORED,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    trigger_execution_id VARCHAR(150) GENERATED ALWAYS AS (value #>> '{trigger, variables, executionId}') STORED,
    kind VARCHAR(32) GENERATED ALWAYS AS (value ->> 'kind') STORED
);

CREATE INDEX IF NOT EXISTS executions_namespace ON executions (deleted, tenant_id, namespace);
CREATE INDEX IF NOT EXISTS executions_flow_id ON executions (deleted, tenant_id, flow_id);
CREATE INDEX IF NOT EXISTS executions_state_current ON executions (deleted, tenant_id, state_current);
CREATE INDEX IF NOT EXISTS executions_start_date ON executions (deleted, tenant_id, start_date);
CREATE INDEX IF NOT EXISTS executions_end_date ON executions (deleted, tenant_id, end_date);
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions (deleted, tenant_id, state_duration);
CREATE INDEX IF NOT EXISTS executions_fulltext ON executions USING GIN (fulltext);
CREATE INDEX IF NOT EXISTS executions_trigger_execution_id ON executions (deleted, tenant_id, trigger_execution_id);
CREATE INDEX IF NOT EXISTS executions_labels ON executions USING GIN ((value -> 'labels'));


/* ----------------------- triggers ----------------------- */
CREATE TABLE IF NOT EXISTS triggers (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    trigger_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'triggerId') STORED,
    execution_id VARCHAR(150) GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
        FULLTEXT_INDEX(CAST(value ->> 'triggerId' AS varchar)) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'executionId' AS varchar), ''))
    ) STORED,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    worker_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'workerId') STORED,
    disabled BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'disabled' AS BOOL)) STORED,
    next_execution_date TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'nextExecutionDate')) STORED
);

CREATE INDEX IF NOT EXISTS triggers_execution_id ON triggers (execution_id);
CREATE INDEX IF NOT EXISTS triggers__tenant ON triggers (tenant_id);
CREATE INDEX IF NOT EXISTS triggers_next_execution_date ON triggers (next_execution_date);


/* ----------------------- logs ----------------------- */
CREATE TABLE IF NOT EXISTS logs (
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

CREATE INDEX IF NOT EXISTS logs_execution_id ON logs (execution_id);
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs (execution_id, task_id);
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs (execution_id, taskrun_id);
CREATE INDEX IF NOT EXISTS logs_fulltext ON logs USING GIN (fulltext);
CREATE INDEX IF NOT EXISTS logs_timestamp ON logs ("timestamp");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX CONCURRENTLY IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE IF NOT EXISTS multipleconditions (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    condition_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'conditionId') STORED,
    start_date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'start')) STORED,
    end_date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'end')) STORED,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED
);

CREATE INDEX IF NOT EXISTS multipleconditions_namespace__flow_id__condition_id ON multipleconditions (tenant_id, namespace, flow_id, condition_id);
CREATE INDEX IF NOT EXISTS multipleconditions_start_date__end_date ON multipleconditions (tenant_id, start_date, end_date);


/* ----------------------- executordelayed ----------------------- */
CREATE TABLE IF NOT EXISTS executordelayed (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED
);

CREATE INDEX IF NOT EXISTS executordelayed_date ON executordelayed (date);


/* ----------------------- settings ----------------------- */
CREATE TABLE IF NOT EXISTS settings (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL
);


/* ----------------------- flow_topologies ----------------------- */
CREATE TABLE IF NOT EXISTS flow_topologies (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    source_namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{source, namespace}') STORED,
    source_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{source, id}') STORED,
    relation VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'relation') STORED,
    destination_namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{destination, namespace}') STORED,
    destination_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value #>> '{destination, id}') STORED,
    source_tenant_id VARCHAR(250) GENERATED ALWAYS AS (value #>> '{source, tenantId}') STORED,
    destination_tenant_id VARCHAR(250) GENERATED ALWAYS AS (value #>> '{destination, tenantId}') STORED
);

CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies (destination_tenant_id, destination_namespace, destination_id);
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies (destination_tenant_id, destination_namespace, destination_id, source_tenant_id, source_namespace, source_id);


/* ----------------------- metrics ----------------------- */
CREATE TABLE IF NOT EXISTS metrics (
    key VARCHAR(30) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    task_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskId') STORED,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    taskrun_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'taskRunId') STORED,
    metric_name VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    timestamp TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'timestamp')) STORED,
    metric_value FLOAT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'value' AS FLOAT)) STORED,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    execution_kind VARCHAR(32) GENERATED ALWAYS AS (value ->> 'executionKind') STORED
);

CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_flow_id ON metrics (tenant_id, namespace, flow_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_execution_id ON metrics (execution_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS metrics_timestamp ON metrics (tenant_id, timestamp);


/* ----------------------- execution_queued ----------------------- */
CREATE TABLE IF NOT EXISTS execution_queued (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    date TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED
);

CREATE INDEX IF NOT EXISTS execution_queued__flow_date ON execution_queued (tenant_id, namespace, flow_id, date);


/* ----------------------- sla_monitor ----------------------- */
CREATE TABLE IF NOT EXISTS sla_monitor (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    execution_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'executionId') STORED,
    sla_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'slaId') STORED,
    deadline TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'deadline')) STORED
);

CREATE INDEX IF NOT EXISTS sla_monitor__deadline ON sla_monitor (deadline);
CREATE INDEX IF NOT EXISTS sla_monitor__execution_id ON sla_monitor (execution_id);


/* ----------------------- dashboards ----------------------- */
CREATE TABLE IF NOT EXISTS dashboards (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    id VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    title VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'title') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value->>'title' AS VARCHAR))
    ) STORED,
    source_code TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS dashboards_tenant ON dashboards (deleted, tenant_id);
CREATE INDEX IF NOT EXISTS dashboards_id ON dashboards (id, deleted, tenant_id);
CREATE INDEX IF NOT EXISTS dashboards_fulltext ON dashboards USING GIN (fulltext);

CREATE OR REPLACE TRIGGER dashboard_updated BEFORE UPDATE
    ON dashboards FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* ----------------------- service_instance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance (
    key             VARCHAR(250) NOT NULL PRIMARY KEY,
    value           JSONB        NOT NULL,
    service_id      VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    service_type    VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'type') STORED,
    state           VARCHAR(36)  NOT NULL GENERATED ALWAYS AS (value ->> 'state') STORED,
    created_at      TIMESTAMPTZ  NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'createdAt')) STORED,
    updated_at      TIMESTAMPTZ  NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updatedAt')) STORED
);

CREATE INDEX IF NOT EXISTS ix_service_instance_state ON service_instance (state);
CREATE INDEX IF NOT EXISTS ix_service_instance_type_created_at_updated_at ON service_instance (service_type, created_at, updated_at);
CREATE UNIQUE INDEX IF NOT EXISTS ix_service_id ON service_instance (service_id);


/* ----------------------- concurrency_limit ----------------------- */
CREATE TABLE IF NOT EXISTS concurrency_limit (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    running INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'running' AS INTEGER)) STORED
);

CREATE INDEX IF NOT EXISTS concurrency_limit__flow ON concurrency_limit (tenant_id, namespace, flow_id);


/* ----------------------- kv_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    "description" TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "expiration_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'expirationDate')) STORED,
    "created" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(COALESCE(value ->> 'created', value ->> 'updated'))) STORED,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'name' AS varchar))
    ) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");

CREATE OR REPLACE TRIGGER kv_metadata_updated BEFORE UPDATE
    ON kv_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* ----------------------- namespace_file_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'path') STORED,
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (value ->> 'parentPath') STORED,
    "version" INT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'version' AS INTEGER)) STORED,
    "last" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'last' AS BOOL)) STORED,
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'size' AS BIGINT)) STORED,
    "created" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'created')) STORED,
    "updated" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updated')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (FULLTEXT_INDEX(CAST(value ->> 'path' AS varchar))) STORED
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");

CREATE OR REPLACE TRIGGER namespace_file_metadata_updated BEFORE UPDATE
    ON namespace_file_metadata FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


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


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executorstate (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL
);


/* ----------------------- worker_job_running ----------------------- */
CREATE TABLE IF NOT EXISTS worker_job_running (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    worker_uuid VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value -> 'workerInstance' ->> 'workerUuid') STORED
);

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uuid ON worker_job_running (worker_uuid);
