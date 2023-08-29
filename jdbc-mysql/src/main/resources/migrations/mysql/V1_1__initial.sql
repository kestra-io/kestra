DELIMITER //
CREATE FUNCTION IF NOT EXISTS PARSE_ISO8601_DURATION(duration VARCHAR(20))
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
CREATE FUNCTION IF NOT EXISTS PARSE_ISO8601_DATETIME(date VARCHAR(50))
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

CREATE TABLE IF NOT EXISTS queues (
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
        'worker',
        'scheduler'
    ),
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_type__consumers (type, consumers, offset),
    INDEX ix_type__offset (type, offset),
    INDEX ix_updated (updated)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS `flows` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `revision` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.revision') STORED NOT NULL,
    `source_code` TEXT NOT NULL,
    INDEX ix_namespace (deleted, namespace),
    INDEX ix_namespace__id__revision (deleted, namespace, id, revision),
    FULLTEXT ix_fulltext (namespace, id),
    FULLTEXT ix_source_code (source_code)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS `templates` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    INDEX ix_namespace (deleted, namespace),
    INDEX ix_namespace__id (deleted, namespace, id),
    FULLTEXT ix_fulltext (namespace, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS `executions` (
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
    `end_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.state.endDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED,
    INDEX ix_namespace (deleted, namespace),
    INDEX ix_flowId (deleted, flow_id),
    INDEX ix_state_current (deleted, state_current),
    INDEX ix_start_date (deleted, start_date),
    INDEX ix_end_date (deleted, end_date),
    INDEX ix_state_duration (deleted, state_duration),
    FULLTEXT ix_fulltext (namespace, flow_id, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS triggers (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.triggerId') STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED ,
    INDEX ix_execution_id (execution_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS logs (
    `key` VARCHAR(30) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
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
    INDEX ix_execution_id (deleted, execution_id),
    INDEX ix_execution_id__task_id (deleted, execution_id, task_id),
    INDEX ix_execution_id__taskrun_id (deleted, execution_id, taskrun_id),
    FULLTEXT ix_fulltext (namespace, flow_id, task_id, execution_id, taskrun_id, trigger_id, message, thread)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS multipleconditions (
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
    INDEX ix_namespace__flow_id__condition_id (namespace, flow_id, condition_id),
    INDEX ix_start_date__end_date (start_date, end_date)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS workertaskexecutions (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS executorstate (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS executordelayed (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (
        IF(
            SUBSTRING(value ->> '$.date', LENGTH(value ->> '$.date'), LENGTH(value ->> '$.date')) = 'Z',
            STR_TO_DATE(value ->> '$.date', '%Y-%m-%dT%H:%i:%s.%fZ'),
            CONVERT_TZ(
                STR_TO_DATE(SUBSTRING(value ->> '$.date', 1, LENGTH(value ->> '$.date') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                SUBSTRING(value ->> '$.date', LENGTH(value ->> '$.date') - 5, 5),
                'UTC'
            )
        )
    ) STORED NOT NULL,
    INDEX ix_date (`date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

/* ---!!! previously on V2__setting.sql !!!--- */
CREATE TABLE IF NOT EXISTS settings (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

/* ---!!! previously on V5__flow_topologies.sql !!!--- */
CREATE TABLE IF NOT EXISTS `flow_topologies` (
     `key` VARCHAR(250) NOT NULL PRIMARY KEY,
     `value` JSON NOT NULL,
     `source_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.namespace') STORED NOT NULL,
     `source_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.id') STORED NOT NULL,
     `relation` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.relation') STORED NOT NULL,
     `destination_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.namespace') STORED NOT NULL,
     `destination_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.id') STORED NOT NULL,
     INDEX ix_destination (destination_namespace, destination_id),
     INDEX ix_destination__source (destination_namespace, destination_id, source_namespace, source_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


ALTER TABLE queues CHANGE consumers consumers SET(
    'indexer',
    'executor',
    'worker',
    'scheduler',
    'flow_topology'
);

/* ----------------------- metrics ----------------------- */
/* ---!!! previously on V8__metrics.sql !!!--- */

CREATE TABLE IF NOT EXISTS metrics (
                         `key` VARCHAR(30) NOT NULL PRIMARY KEY,
                         `value` JSON NOT NULL,
                         `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
                         `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
                         `flow_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
                         `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED,
                         `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
                         `taskrun_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
                         `metric_name` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.name') STORED,
                         `timestamp` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.timestamp' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
                         INDEX ix_metrics_flow_id (deleted, namespace, flow_id),
                         INDEX ix_metrics_execution_id (deleted, execution_id),
                         INDEX ix_metrics_timestamp (deleted, timestamp)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

/* ---!!! previously on V10__metric_missing_value.sql !!!--- */
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    ALTER TABLE metrics ADD COLUMN metric_value FLOAT GENERATED ALWAYS AS (value ->> '$.value') STORED NOT NULL;
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;

/* ---!!! previously on V11__queues_consumer_group.sql !!!--- */
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    ALTER TABLE queues ADD COLUMN consumer_group VARCHAR(250);
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;

/* ---!!! previously on V14__polling_trigger.sql !!!--- */
ALTER TABLE queues MODIFY COLUMN `type`
    ENUM(
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
ALTER TABLE logs MODIFY COLUMN execution_id varchar(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NULL;

-- Update WorkerTask and WorkerTrigger to WorkerJob then delete the two enums that are no more used
UPDATE queues SET `type` = 'io.kestra.core.runners.WorkerJob'
WHERE `type` = 'io.kestra.core.runners.WorkerTask' OR `type` = 'io.kestra.core.runners.WorkerTrigger';

ALTER TABLE queues MODIFY COLUMN `type`
    ENUM(
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

/* ---!!! previously on V15__trigger_fulltext.sql !!!--- */
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    ALTER TABLE triggers ADD FULLTEXT ix_fulltext (namespace, flow_id, trigger_id, execution_id);
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;

/* ---!!! previously on V16__index_logs.sql !!!--- */
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    DROP INDEX ix_namespace ON logs;
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;

DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    DROP INDEX ix_timestamp ON logs;
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;

DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `?`()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    CREATE INDEX ix_namespace_flow ON logs (deleted, timestamp, level, namespace, flow_id);
END //
DELIMITER ;
CALL `?`();
DROP PROCEDURE `?`;