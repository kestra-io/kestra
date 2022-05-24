DELIMITER //
CREATE FUNCTION PARSE_ISO8601_DURATION(duration VARCHAR(20))
    RETURNS bigint
    LANGUAGE SQL
    CONTAINS SQL
    DETERMINISTIC
BEGIN
    RETURN
        CASE
            WHEN duration LIKE 'P%DT%H%M%.%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'P%dDT%HH%iM%s.%fS.%f'))
            WHEN duration LIKE 'P%DT%H%M%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'P%dDT%HH%iM%sS.%f'))
            WHEN duration LIKE 'PT%H%M%.%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%HH%iM%s.%fS.%f'))
            WHEN duration LIKE 'PT%H%M%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%HH%iM%sS.%f'))
            WHEN duration LIKE 'PT%M%.%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%iM%s.%fS.%f'))
            WHEN duration LIKE 'PT%M%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%iM%sS.%f'))
            WHEN duration LIKE 'PT%.%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%s.%fS.%f'))
            WHEN duration LIKE 'PT%S' THEN TO_SECONDS(STR_TO_DATE(duration, 'PT%sS.%f'))
            END;

END //
DELIMITER ;

DELIMITER //
CREATE FUNCTION PARSE_ISO8601_DATETIME(date VARCHAR(50))
    RETURNS datetime
    LANGUAGE SQL
    CONTAINS SQL
    DETERMINISTIC
BEGIN
    RETURN IF(
        SUBSTRING(date, LENGTH(date), LENGTH(date)) = 'Z',
        STR_TO_DATE(date, '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(date, 1, LENGTH(date) - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            SUBSTRING(date, LENGTH(date) - 5, 5),
            'UTC'
        )
    );
END //
DELIMITER ;

CREATE TABLE queues (
    `offset` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type` ENUM(
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
    `key` VARCHAR(250) NOT NULL,
    `value` JSON NOT NULL,
    `consumers` SET(
        'indexer',
        'executor',
        'worker'
    )
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE `flows` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `revision` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.revision') STORED NOT NULL,
    `source_code` TEXT NOT NULL,
    INDEX ix_id (id),
    INDEX ix_namespace (namespace),
    INDEX ix_revision (revision),
    INDEX ix_deleted (deleted),
    FULLTEXT ix_fulltext (namespace, id),
    FULLTEXT ix_source_code (source_code)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE `templates` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    INDEX ix_id (id),
    INDEX ix_namespace (namespace),
    INDEX ix_deleted (deleted),
    FULLTEXT ix_fulltext (namespace, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE `executions` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `state_current` ENUM(
        'CREATED',
        'RUNNING',
        'PAUSED',
        'RESTARTED',
        'KILLING',
        'SUCCESS',
        'WARNING',
        'FAILED',
        'KILLED'
    ) GENERATED ALWAYS AS (value ->> '$.state.current') STORED NOT NULL,
    `state_duration` BIGINT GENERATED ALWAYS AS (value ->> '$.state.duration' * 1000) STORED NOT NULL,
    `start_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.state.startDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_id (id),
    INDEX ix_namespace (namespace),
    INDEX ix_flowId (flow_id),
    INDEX ix_state_current (state_current),
    INDEX ix_start_date (start_date),
    INDEX ix_state_duration (state_duration),
    INDEX ix_deleted (deleted),
    FULLTEXT ix_fulltext (namespace, flow_id, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE triggers (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.triggerId') STORED NOT NULL,
    INDEX ix_executions_id (namespace, flow_id, trigger_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE logs (
    `key` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `taskrun_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
    `attempt_number` INT GENERATED ALWAYS AS (value ->> '$.attemptNumber') STORED NOT NULL,
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

    INDEX ix_namespace (namespace),
    INDEX ix_flowId (flow_id),
    INDEX ix_task_id (task_id),
    INDEX ix_execution_id (execution_id),
    INDEX ix_taskrun_id (taskrun_id),
    INDEX ix_trigger_id (trigger_id),
    INDEX ix_timestamp (timestamp),
    FULLTEXT ix_fulltext (namespace, flow_id, task_id, execution_id, taskrun_id, trigger_id, message, thread)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE multipleconditions (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `condition_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.conditionId') STORED NOT NULL,
    `start_date` DATETIME(6) GENERATED ALWAYS AS (
        IF(
            SUBSTRING(value ->> '$.start', LENGTH(value ->> '$.start'), LENGTH(value ->> '$.start')) = 'Z',
            STR_TO_DATE(value ->> '$.start', '%Y-%m-%dT%H:%i:%s.%fZ'),
            CONVERT_TZ(
                STR_TO_DATE(SUBSTRING(value ->> '$.start', 1, LENGTH(value ->> '$.start') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                SUBSTRING(value ->> '$.start', LENGTH(value ->> '$.start') - 5, 5),
                'UTC'
                )
        )
    ) STORED NOT NULL,
    `end_date` DATETIME(6) GENERATED ALWAYS AS (
        IF(
            SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end'), LENGTH(value ->> '$.end')) = 'Z',
            STR_TO_DATE(value ->> '$.end', '%Y-%m-%dT%H:%i:%s.%fZ'),
            CONVERT_TZ(
                STR_TO_DATE(SUBSTRING(value ->> '$.end', 1, LENGTH(value ->> '$.end') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end') - 5, 5),
                'UTC'
            )
        )
    ) STORED NOT NULL,
    INDEX namespace__flow_id__condition_id (namespace, flow_id, condition_id),
    INDEX start_date__end_date (start_date, end_date)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
