/* ----------------------- functions ----------------------- */
CREATE ALIAS IF NOT EXISTS JQ_STRING FOR "io.kestra.runner.h2.H2Functions.jqString" ;
CREATE ALIAS IF NOT EXISTS JQ_BOOLEAN FOR "io.kestra.runner.h2.H2Functions.jqBoolean" ;
CREATE ALIAS IF NOT EXISTS JQ_LONG FOR "io.kestra.runner.h2.H2Functions.jqLong" ;
CREATE ALIAS IF NOT EXISTS JQ_INTEGER FOR "io.kestra.runner.h2.H2Functions.jqInteger" ;
CREATE ALIAS IF NOT EXISTS JQ_DOUBLE FOR "io.kestra.runner.h2.H2Functions.jqDouble" ;
CREATE ALIAS IF NOT EXISTS JQ_STRING_ARRAY FOR "io.kestra.runner.h2.H2Functions.jqStringArray" ;

/* ----------------------- queues ----------------------- */
CREATE TABLE IF NOT EXISTS queues (
    "offset" INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type" ENUM (
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
    ) NOT NULL,
    "key" VARCHAR(250) NOT NULL,
    "value" TEXT NOT NULL,
    "consumers" ENUM(
        'indexer',
        'executor',
        'worker',
        'scheduler'
    ) ARRAY,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS queues_type__consumers ON queues ("type", "consumers", "offset");
CREATE INDEX IF NOT EXISTS queues_type__offset ON queues ("type", "offset");
CREATE INDEX IF NOT EXISTS queues_updated ON queues ("updated");


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
    "source_code" TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS flows_namespace ON flows ("deleted", "namespace");
CREATE INDEX IF NOT EXISTS flows_namespace__id__revision ON flows ("deleted", "namespace", "id", "revision");


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


/* ----------------------- executions ----------------------- */
CREATE TABLE IF NOT EXISTS executions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
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
        'KILLED'
    ) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state.current')),
    "state_duration" FLOAT NOT NULL GENERATED ALWAYS AS (JQ_DOUBLE("value", '.state.duration')),
    "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.state.startDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSS''Z''')),
    "end_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.state.endDate'), 'yyyy-MM-dd''T''HH:mm:ss.SSS''Z''')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.id') || JQ_STRING("value", '.namespace') || JQ_STRING("value", '.flowId')
    )
);

CREATE INDEX IF NOT EXISTS executions_namespace ON executions ("deleted", "namespace");
CREATE INDEX IF NOT EXISTS executions_flow_id ON executions ("deleted", "flow_id");
CREATE INDEX IF NOT EXISTS executions_state_current ON executions ("deleted", "state_current");
CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "start_date");
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "end_date");
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions ("deleted", "state_duration");


/* ----------------------- triggers ----------------------- */
CREATE TABLE IF NOT EXISTS triggers (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "trigger_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId'))
);

CREATE INDEX IF NOT EXISTS triggers_execution_id ON triggers ("execution_id");


/* ----------------------- logs ----------------------- */
CREATE TABLE IF NOT EXISTS logs (
    "key" VARCHAR(30) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
    "attempt_number" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.attemptNumber')),
    "trigger_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "message" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.message')),
    "thread" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.thread')),
    "level"  ENUM (
    'ERROR',
    'WARN',
    'INFO',
    'DEBUG',
    'TRACE'
    ) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.level')),
    "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.timestamp'), 'yyyy-MM-dd''T''HH:mm:ss.SSS''Z''')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
            JQ_STRING("value", '.namespace') ||
            JQ_STRING("value", '.flowId') ||
            COALESCE(JQ_STRING("value", '.taskId'), '') ||
            JQ_STRING("value", '.executionId') ||
            COALESCE(JQ_STRING("value", '.taskRunId'), '') ||
            COALESCE(JQ_STRING("value", '.triggerId'), '') ||
            COALESCE(JQ_STRING("value", '.message'), '') ||
            COALESCE(JQ_STRING("value", '.thread'), '')
        )
);

CREATE INDEX IF NOT EXISTS logs_execution_id ON logs ("deleted", "execution_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs ("deleted", "execution_id", "task_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs ("deleted", "execution_id", "taskrun_id");


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE IF NOT EXISTS multipleconditions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "condition_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.conditionId')),
    "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.start'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "end_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.end'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS multipleconditions_namespace__flow_id__condition_id ON multipleconditions ("namespace", "flow_id", "condition_id");
CREATE INDEX IF NOT EXISTS multipleconditions_start_date__end_date ON multipleconditions ("start_date", "end_date");


/* ----------------------- workertaskexecutions ----------------------- */
CREATE TABLE IF NOT EXISTS workertaskexecutions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executorstate (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS executordelayed (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.date'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS executordelayed_date ON executordelayed ("date");

/* ----------------------- settings ----------------------- */
/* ---!!! previously on V2__setting.sql !!!--- */
CREATE TABLE IF NOT EXISTS settings (
  "key" VARCHAR(250) NOT NULL PRIMARY KEY,
  "value" TEXT NOT NULL
);

/* ----------------------- flow_topologies ----------------------- */
/* ---!!! previously on V5__flow_topologies.sql !!!--- */
CREATE TABLE IF NOT EXISTS flow_topologies (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "source_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.namespace')),
    "source_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.source.id')),
    "relation" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.relation')),
    "destination_namespace" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.namespace')),
    "destination_id" VARCHAR(255) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.destination.id'))
    );

CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies ("destination_namespace", "destination_id");
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies ("destination_namespace", "destination_id", "source_namespace", "source_id");

ALTER TABLE queues
ALTER COLUMN "consumers" ENUM(
        'indexer',
        'executor',
        'worker',
        'scheduler',
        'flow_topology'
        ) ARRAY;

/* ----------------------- missing execution id ----------------------- */
/* ---!!! previously on V6__missing_execution_id.sql !!!--- */
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id'));

/* ----------------------- metrics ----------------------- */
/* ---!!! previously on V11__metrics.sql !!!--- */
CREATE TABLE IF NOT EXISTS metrics (
    "key" VARCHAR(30) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "task_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskId')),
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "taskrun_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.taskRunId')),
    "metric_name" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.timestamp'), 'yyyy-MM-dd''T''HH:mm:ss.SSS''Z'''))
    );

ALTER TABLE metrics ADD COLUMN IF NOT EXISTS "metric_value" DOUBLE GENERATED ALWAYS AS (JQ_DOUBLE("value", '.value'));

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;
CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("deleted", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics ("deleted", "execution_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("deleted", "timestamp");

/* ----------------------- queues consumer group ----------------------- */
/* ---!!! previously on V13__queues_consumer_group.sql !!!--- */
ALTER TABLE queues ADD COLUMN IF NOT EXISTS "consumer_group" VARCHAR(250);

/* ----------------------- polling trigger ----------------------- */
/* ---!!! previously on V16__polling_trigger.sql !!!--- */
ALTER TABLE queues
ALTER COLUMN "type" ENUM(
    'io.kestra.core.models.executions.Execution',
    'io.kestra.core.models.flows.Flow',
    'io.kestra.core.models.templates.Template',
    'io.kestra.core.models.executions.ExecutionKilled',
    'io.kestra.core.runners.WorkerJob',
    'io.kestra.core.runners.WorkerTask',
    'io.kestra.core.runners.WorkerTaskResult',
    'io.kestra.core.runners.WorkerInstance',
    'io.kestra.core.runners.WorkerTaskRunning',
    'io.kestra.core.models.executions.LogEntry',
    'io.kestra.core.models.triggers.Trigger',
    'io.kestra.ee.models.audits.AuditLog',
    'io.kestra.core.models.executions.MetricEntry',
    'io.kestra.core.runners.WorkerTrigger',
    'io.kestra.core.runners.WorkerTriggerResult'
) NOT NULL;

-- trigger logs have no execution id
alter table logs alter column "execution_id" set null;

-- Update WorkerTask and WorkerTrigger to WorkerJob then delete the two enums that are no more used
UPDATE queues SET "type" = 'io.kestra.core.runners.WorkerJob'
WHERE "type" = 'io.kestra.core.runners.WorkerTask' OR "type" = 'io.kestra.core.runners.WorkerTrigger';

ALTER TABLE queues
ALTER COLUMN "type" ENUM(
    'io.kestra.core.models.executions.Execution',
    'io.kestra.core.models.flows.Flow',
    'io.kestra.core.models.templates.Template',
    'io.kestra.core.models.executions.ExecutionKilled',
    'io.kestra.core.runners.WorkerJob',
    'io.kestra.core.runners.WorkerTaskResult',
    'io.kestra.core.runners.WorkerInstance',
    'io.kestra.core.runners.WorkerTaskRunning',
    'io.kestra.core.models.executions.LogEntry',
    'io.kestra.core.models.triggers.Trigger',
    'io.kestra.ee.models.audits.AuditLog',
    'io.kestra.core.models.executions.MetricEntry',
    'io.kestra.core.runners.WorkerTriggerResult'
) NOT NULL;

/* ----------------------- trigger full text ----------------------- */
/* ---!!! previously on V17__trigger_fulltext_col.sql !!!--- */
ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
    JQ_STRING("value", '.flowId') ||
    JQ_STRING("value", '.namespace') ||
    JQ_STRING("value", '.triggerId') ||
    COALESCE(JQ_STRING("value", '.executionId'), '')
);

/* -----------------------index logs ----------------------- */
/* ---!!! previously on V18__index_logs.sql !!!--- */
DROP INDEX IF EXISTS logs_namespace;
DROP INDEX IF EXISTS logs_timestamp;
CREATE INDEX IF NOT EXISTS logs_namespace_flow ON logs ("deleted", "timestamp", "level", "namespace", "flow_id");