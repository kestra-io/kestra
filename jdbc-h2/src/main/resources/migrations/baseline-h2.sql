-- OSS H2 baseline schema — represents the Kestra 1.3 (Flyway-era) database.
-- The 2.0 upgrade script (upgrade-v2.0-h2.sql) bridges the gap from this schema to 2.0.

/* ----------------------- functions ----------------------- */
CREATE ALIAS IF NOT EXISTS JQ_STRING FOR "io.kestra.runner.h2.H2Functions.jqString" ;
CREATE ALIAS IF NOT EXISTS JQ_BOOLEAN FOR "io.kestra.runner.h2.H2Functions.jqBoolean" ;
CREATE ALIAS IF NOT EXISTS JQ_LONG FOR "io.kestra.runner.h2.H2Functions.jqLong" ;
CREATE ALIAS IF NOT EXISTS JQ_INTEGER FOR "io.kestra.runner.h2.H2Functions.jqInteger" ;
CREATE ALIAS IF NOT EXISTS JQ_DOUBLE FOR "io.kestra.runner.h2.H2Functions.jqDouble" ;
CREATE ALIAS IF NOT EXISTS JQ_STRING_ARRAY FOR "io.kestra.runner.h2.H2Functions.jqStringArray" ;

/* queues table is created by the separate queue baseline (baseline-queue-h2.sql) */


/* ----------------------- flows ----------------------- */
CREATE TABLE IF NOT EXISTS flows (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "revision" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.revision')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.id') || JQ_STRING("value", '.namespace')
    ),
    "source_code" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "updated" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.updated'))
);

CREATE INDEX IF NOT EXISTS flows_namespace ON flows ("deleted", "tenant_id", "namespace");
CREATE INDEX IF NOT EXISTS flows_namespace__id__revision ON flows ("deleted", "tenant_id", "namespace", "id", "revision");


/* ----------------------- executions ----------------------- */
CREATE TABLE IF NOT EXISTS executions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "state_current" ENUM (
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
    ) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state.current')),
    "state_duration" FLOAT GENERATED ALWAYS AS (JQ_DOUBLE("value", '.state.duration')),
    "start_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.state.startDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "end_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.state.endDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.id') || JQ_STRING("value", '.namespace') || JQ_STRING("value", '.flowId')
    ),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "trigger_execution_id" VARCHAR(100) GENERATED ALWAYS AS (JQ_STRING("value", '.trigger.variables.executionId')),
    "kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.kind'))
);

CREATE INDEX IF NOT EXISTS executions_namespace ON executions ("deleted", "tenant_id", "namespace");
CREATE INDEX IF NOT EXISTS executions_flow_id ON executions ("deleted", "tenant_id", "flow_id");
CREATE INDEX IF NOT EXISTS executions_state_current ON executions ("deleted", "tenant_id", "state_current");
CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "start_date");
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "end_date");
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions ("deleted", "tenant_id", "state_duration");
CREATE INDEX IF NOT EXISTS executions_trigger_execution_id ON executions ("deleted", "tenant_id", "trigger_execution_id");


/* ----------------------- triggers ----------------------- */
CREATE TABLE IF NOT EXISTS triggers (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "trigger_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.flowId') ||
        JQ_STRING("value", '.namespace') ||
        JQ_STRING("value", '.triggerId') ||
        COALESCE(JQ_STRING("value", '.executionId'), '')
    ),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "worker_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.workerId')),
    "disabled" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.disabled')),
    "next_execution_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.nextExecutionDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS triggers_execution_id ON triggers ("execution_id");
CREATE INDEX IF NOT EXISTS triggers__tenant ON triggers ("tenant_id");
CREATE INDEX IF NOT EXISTS ix_next_execution_date ON triggers ("next_execution_date");


/* ----------------------- logs ----------------------- */
CREATE TABLE IF NOT EXISTS logs (
    "key" VARCHAR(30) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
    "attempt_number" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.attemptNumber')),
    "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "message" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.message')),
    "thread" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.thread')),
    "level" ENUM (
        'ERROR',
        'WARN',
        'INFO',
        'DEBUG',
        'TRACE'
    ) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.level')),
    "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.timestamp'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.namespace') ||
        JQ_STRING("value", '.flowId') ||
        COALESCE(JQ_STRING("value", '.taskId'), '') ||
        COALESCE(JQ_STRING("value", '.executionId'), '') ||
        COALESCE(JQ_STRING("value", '.taskRunId'), '') ||
        COALESCE(JQ_STRING("value", '.triggerId'), '') ||
        COALESCE(JQ_STRING("value", '.message'), '') ||
        COALESCE(JQ_STRING("value", '.thread'), '')
    ),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.executionKind'))
);

CREATE INDEX IF NOT EXISTS logs_execution_id ON logs ("execution_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs ("execution_id", "task_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs ("execution_id", "taskrun_id");
CREATE INDEX IF NOT EXISTS logs_timestamp ON logs ("timestamp");
CREATE INDEX IF NOT EXISTS logs_tenant_timestamp ON logs ("tenant_id", "timestamp", "level");
CREATE INDEX IF NOT EXISTS logs_tenant_namespace_timestamp ON logs ("tenant_id", "namespace", "timestamp", "level");


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE IF NOT EXISTS multipleconditions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "condition_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.conditionId')),
    "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.start'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "end_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.end'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'))
);

CREATE INDEX IF NOT EXISTS multipleconditions_namespace__flow_id__condition_id ON multipleconditions ("tenant_id", "namespace", "flow_id", "condition_id");
CREATE INDEX IF NOT EXISTS multipleconditions_start_date__end_date ON multipleconditions ("tenant_id", "start_date", "end_date");


/* ----------------------- executordelayed ----------------------- */
CREATE TABLE IF NOT EXISTS executordelayed (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS executordelayed_date ON executordelayed ("date");


/* ----------------------- settings ----------------------- */
CREATE TABLE IF NOT EXISTS settings (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- flow_topologies ----------------------- */
CREATE TABLE IF NOT EXISTS flow_topologies (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "source_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.namespace')),
    "source_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.id')),
    "relation" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.relation')),
    "destination_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.namespace')),
    "destination_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.id')),
    "source_tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.source.tenantId')),
    "destination_tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.destination.tenantId'))
);

CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies ("destination_tenant_id", "destination_namespace", "destination_id");
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies ("destination_tenant_id", "destination_namespace", "destination_id", "source_tenant_id", "source_namespace", "source_id");


/* ----------------------- metrics ----------------------- */
CREATE TABLE IF NOT EXISTS metrics (
    "key" VARCHAR(30) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
    "metric_name" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.timestamp'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "metric_value" DOUBLE GENERATED ALWAYS AS (JQ_DOUBLE("value", '.value')),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.executionKind'))
);

CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("tenant_id", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics ("execution_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("tenant_id", "timestamp");


/* ----------------------- execution_queued ----------------------- */
CREATE TABLE IF NOT EXISTS execution_queued (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS execution_queued__flow_date ON execution_queued ("tenant_id", "namespace", "flow_id", "date");


/* ----------------------- sla_monitor ----------------------- */
CREATE TABLE IF NOT EXISTS sla_monitor (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "sla_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.slaId')),
    "deadline" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.deadline'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS sla_monitor__deadline ON sla_monitor ("deadline");
CREATE INDEX IF NOT EXISTS sla_monitor__execution_id ON sla_monitor ("execution_id");


/* ----------------------- dashboards ----------------------- */
CREATE TABLE IF NOT EXISTS dashboards (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "title" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.title')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.title')
    ),
    "source_code" TEXT NOT NULL,
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS dashboards_tenant ON dashboards ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS dashboards_id ON dashboards ("id", "deleted", "tenant_id");


/* ----------------------- service_instance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance (
    "key"          VARCHAR(250)  NOT NULL PRIMARY KEY,
    "value"        CLOB NOT NULL,
    "service_id"   VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "service_type" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.type')),
    "state"        VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state')),
    "created_at"   TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.createdAt'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated_at"   TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updatedAt'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS ix_service_instance_state ON service_instance ("state");
CREATE INDEX IF NOT EXISTS ix_service_instance_type_created_at_updated_at ON service_instance ("service_type", "created_at", "updated_at");
CREATE UNIQUE INDEX IF NOT EXISTS ix_service_id ON service_instance ("service_id");


/* ----------------------- concurrency_limit ----------------------- */
CREATE TABLE IF NOT EXISTS concurrency_limit (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "running" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.running'))
);

CREATE INDEX IF NOT EXISTS concurrency_limit__flow ON concurrency_limit ("tenant_id", "namespace", "flow_id");


/* ----------------------- kv_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS kv_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "expiration_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.expirationDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSSSSS''Z''')),
    "created" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(COALESCE(JQ_STRING("value", '.created'), JQ_STRING("value", '.updated')), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name'))
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_name ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "name");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON kv_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_name_version ON kv_metadata ("last", "deleted", "tenant_id", "name", "version");


/* ----------------------- namespace_file_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "path" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path')),
    "parent_path" VARCHAR(350) GENERATED ALWAYS AS (JQ_STRING("value", '.parentPath')),
    "version" INT NOT NULL GENERATED ALWAYS AS (JQ_INTEGER("value", '.version')),
    "last" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.last')),
    "size" BIGINT NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.size')),
    "created" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.created'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updated'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.path'))
);

CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_parent_path ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "parent_path");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_namespace_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "namespace", "version");
CREATE INDEX IF NOT EXISTS ix_last_deleted_tenant_path_version ON namespace_file_metadata ("last", "deleted", "tenant_id", "path", "version");


/* ----------------------- templates ----------------------- */
CREATE TABLE IF NOT EXISTS templates (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.id') || JQ_STRING("value", '.namespace')
    )
);

CREATE INDEX IF NOT EXISTS templates_namespace ON templates ("deleted", "namespace");
CREATE INDEX IF NOT EXISTS templates_namespace__id ON templates ("deleted", "namespace", "id");


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executorstate (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- worker_job_running ----------------------- */
CREATE TABLE IF NOT EXISTS worker_job_running (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "worker_uuid" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.workerInstance.workerUuid'))
);

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uuid ON worker_job_running ("worker_uuid");
