/* ----------------------- logs (external log store) ----------------------- */
/*
 * Standalone, idempotent DDL for the MySQL log store, run against the log datasource by
 * V2_0LogsMigration when `kestra.logs.type` is mysql. Mirrors the `logs` table from baseline-mysql.sql
 * with the final index set (baseline + 2.0.11: drops ix_execution_id / ix_timestamp, adds
 * ix_tenant_namespace_flow_id_timestamp), the table name templated via ${table}. The generated
 * columns use built-in JSON operators + STR_TO_DATE, so there are no prerequisite functions. Indexes
 * are declared inline (their names are table-local). Idempotent via CREATE TABLE IF NOT EXISTS.
 */
CREATE TABLE IF NOT EXISTS ${table} (
    `key` VARCHAR(30) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED,
    `taskrun_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
    `attempt_number` INT GENERATED ALWAYS AS (IF(value ->> '$.attemptNumber' = 'null', NULL, value ->> '$.attemptNumber')) STORED,
    `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.triggerId') STORED,
    `message` TEXT GENERATED ALWAYS AS (value ->> '$.message') STORED,
    `thread` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.thread') STORED,
    `level` ENUM(
        'ERROR',
        'WARN',
        'INFO',
        'DEBUG',
        'TRACE'
    ) GENERATED ALWAYS AS (value ->> '$.level') STORED NOT NULL,
    `timestamp` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.timestamp' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `execution_kind` VARCHAR(32) GENERATED ALWAYS AS (value ->> '$.executionKind') STORED,
    INDEX ix_execution_id__task_id (execution_id, task_id),
    INDEX ix_execution_id__taskrun_id (execution_id, taskrun_id),
    INDEX ix_tenant_timestamp (tenant_id, timestamp, level),
    INDEX ix_tenant_namespace_timestamp (tenant_id, namespace, timestamp, level),
    INDEX ix_tenant_namespace_flow_id_timestamp (tenant_id, namespace, flow_id, timestamp, level),
    FULLTEXT ix_fulltext (namespace, flow_id, task_id, execution_id, taskrun_id, trigger_id, message, thread)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
