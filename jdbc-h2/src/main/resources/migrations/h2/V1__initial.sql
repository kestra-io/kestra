/* ----------------------- functions ----------------------- */
CREATE ALIAS JQ_STRING FOR "io.kestra.runner.h2.H2Functions.jqString" ;
CREATE ALIAS JQ_BOOLEAN FOR "io.kestra.runner.h2.H2Functions.jqBoolean" ;
CREATE ALIAS JQ_LONG FOR "io.kestra.runner.h2.H2Functions.jqLong" ;
CREATE ALIAS JQ_INTEGER FOR "io.kestra.runner.h2.H2Functions.jqInteger" ;
CREATE ALIAS JQ_DOUBLE FOR "io.kestra.runner.h2.H2Functions.jqDouble" ;
CREATE ALIAS JQ_STRING_ARRAY FOR "io.kestra.runner.h2.H2Functions.jqStringArray" ;

/* ----------------------- queues ----------------------- */
CREATE TABLE queues (
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

CREATE INDEX queues_type__consumers ON queues ("type", "consumers", "offset");
CREATE INDEX queues_type__offset ON queues ("type", "offset");
CREATE INDEX queues_updated ON queues ("updated");


/* ----------------------- flows ----------------------- */
CREATE TABLE flows (
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

CREATE INDEX flows_namespace ON flows ("deleted", "namespace");
CREATE INDEX flows_namespace__id__revision ON flows ("deleted", "namespace", "id", "revision");


/* ----------------------- templates ----------------------- */
CREATE TABLE templates (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.id') || JQ_STRING("value", '.namespace')
    )
);

CREATE INDEX templates_namespace ON templates ("deleted", "namespace");
CREATE INDEX templates_namespace__id ON templates ("deleted", "namespace", "id");


/* ----------------------- executions ----------------------- */
CREATE TABLE executions (
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

CREATE INDEX executions_namespace ON executions ("deleted", "namespace");
CREATE INDEX executions_flow_id ON executions ("deleted", "flow_id");
CREATE INDEX executions_state_current ON executions ("deleted", "state_current");
CREATE INDEX executions_start_date ON executions ("deleted", "start_date");
CREATE INDEX executions_end_date ON executions ("deleted", "end_date");
CREATE INDEX executions_state_duration ON executions ("deleted", "state_duration");


/* ----------------------- triggers ----------------------- */
CREATE TABLE triggers (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "trigger_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.triggerId')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId'))
);

CREATE INDEX triggers_execution_id ON triggers ("execution_id");


/* ----------------------- logs ----------------------- */
CREATE TABLE logs (
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

CREATE INDEX logs_namespace ON logs ("deleted", "namespace");
CREATE INDEX logs_execution_id ON logs ("deleted", "execution_id");
CREATE INDEX logs_execution_id__task_id ON logs ("deleted", "execution_id", "task_id");
CREATE INDEX logs_execution_id__taskrun_id ON logs ("deleted", "execution_id", "taskrun_id");
CREATE INDEX logs_timestamp ON logs ("deleted", "timestamp");


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE multipleconditions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "condition_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.conditionId')),
    "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.start'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "end_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.end'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX multipleconditions_namespace__flow_id__condition_id ON multipleconditions ("namespace", "flow_id", "condition_id");
CREATE INDEX multipleconditions_start_date__end_date ON multipleconditions ("start_date", "end_date");


/* ----------------------- workertaskexecutions ----------------------- */
CREATE TABLE workertaskexecutions (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE executorstate (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL
);


/* ----------------------- executorstate ----------------------- */
CREATE TABLE executordelayed (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.date'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX executordelayed_date ON executordelayed ("date");
