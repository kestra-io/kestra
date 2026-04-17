-- OSS MySQL baseline schema — represents the Kestra 1.3 (Flyway-era) database.
-- The 2.0 upgrade script (upgrade-v2.0-mysql.sql) bridges the gap from this schema to 2.0.

CREATE FUNCTION IF NOT EXISTS PARSE_ISO8601_DURATION(duration VARCHAR(20))
    RETURNS bigint
    LANGUAGE SQL
    CONTAINS SQL
    DETERMINISTIC
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

CREATE FUNCTION IF NOT EXISTS PARSE_ISO8601_DATETIME(date VARCHAR(50))
    RETURNS datetime
    LANGUAGE SQL
    CONTAINS SQL
    DETERMINISTIC
    RETURN IF(
        SUBSTRING(date, LENGTH(date), LENGTH(date)) = 'Z',
        STR_TO_DATE(date, '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(date, 1, LENGTH(date) - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            SUBSTRING(date, LENGTH(date) - 5, 5),
            'UTC'
        )
    );

/* queues table is created by the separate queue baseline (baseline-queue-mysql.sql) */


/* ----------------------- flows ----------------------- */
CREATE TABLE IF NOT EXISTS `flows` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `revision` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.revision') STORED NOT NULL,
    `source_code` TEXT NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `updated` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.updated') STORED,
    INDEX ix_namespace (deleted, tenant_id, namespace),
    INDEX ix_namespace__id__revision (deleted, tenant_id, namespace, id, revision),
    FULLTEXT ix_fulltext (namespace, id),
    FULLTEXT ix_source_code (source_code)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- executions ----------------------- */
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
        'KILLED',
        'CANCELLED',
        'QUEUED',
        'RETRYING',
        'RETRIED',
        'SKIPPED',
        'BREAKPOINT',
        'SUBMITTED',
        'RESUBMITTED'
    ) GENERATED ALWAYS AS (value ->> '$.state.current') STORED NOT NULL,
    `state_duration` BIGINT GENERATED ALWAYS AS (value ->> '$.state.duration' * 1000) STORED,
    `start_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.state.startDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `end_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.state.endDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `trigger_execution_id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.trigger.variables.executionId') STORED,
    `kind` VARCHAR(32) GENERATED ALWAYS AS (value ->> '$.kind') STORED,
    INDEX ix_namespace (deleted, tenant_id, namespace),
    INDEX ix_flowId (deleted, tenant_id, flow_id),
    INDEX ix_state_current (deleted, tenant_id, state_current),
    INDEX ix_start_date (deleted, tenant_id, start_date),
    INDEX ix_end_date (deleted, tenant_id, end_date),
    INDEX ix_state_duration (deleted, tenant_id, state_duration),
    INDEX ix_trigger_execution_id (deleted, tenant_id, trigger_execution_id),
    FULLTEXT ix_fulltext (namespace, flow_id, id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- triggers ----------------------- */
CREATE TABLE IF NOT EXISTS triggers (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.triggerId') STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `worker_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.workerId') STORED,
    `disabled` BOOL GENERATED ALWAYS AS (value ->> '$.disabled' = 'true') STORED NOT NULL,
    `next_execution_date` DATETIME(6) GENERATED ALWAYS AS (
        IF(
            SUBSTRING(value ->> '$.nextExecutionDate', LENGTH(value ->> '$.nextExecutionDate'), LENGTH(value ->> '$.nextExecutionDate')) = 'Z',
            STR_TO_DATE(value ->> '$.nextExecutionDate', '%Y-%m-%dT%H:%i:%s.%fZ'),
            CONVERT_TZ(
                STR_TO_DATE(SUBSTRING(value ->> '$.nextExecutionDate', 1, LENGTH(value ->> '$.nextExecutionDate') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                SUBSTRING(value ->> '$.nextExecutionDate', LENGTH(value ->> '$.nextExecutionDate') - 5, 5),
                'UTC'
                )
        )
    ) STORED,
    INDEX ix_execution_id (execution_id),
    INDEX ix_tenant_id (tenant_id),
    INDEX ix_next_execution_date (next_execution_date),
    FULLTEXT ix_fulltext (namespace, flow_id, trigger_id, execution_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- logs ----------------------- */
CREATE TABLE IF NOT EXISTS logs (
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
    INDEX ix_execution_id (execution_id),
    INDEX ix_execution_id__task_id (execution_id, task_id),
    INDEX ix_execution_id__taskrun_id (execution_id, taskrun_id),
    INDEX ix_timestamp (timestamp),
    INDEX ix_tenant_timestamp (tenant_id, timestamp, level),
    INDEX ix_tenant_namespace_timestamp (tenant_id, namespace, timestamp, level),
    FULLTEXT ix_fulltext (namespace, flow_id, task_id, execution_id, taskrun_id, trigger_id, message, thread)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- multipleconditions ----------------------- */
CREATE TABLE IF NOT EXISTS multipleconditions (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
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
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- executordelayed ----------------------- */
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


/* ----------------------- settings ----------------------- */
CREATE TABLE IF NOT EXISTS settings (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- flow_topologies ----------------------- */
CREATE TABLE IF NOT EXISTS `flow_topologies` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `source_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.namespace') STORED NOT NULL,
    `source_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.source.id') STORED NOT NULL,
    `relation` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.relation') STORED NOT NULL,
    `destination_namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.namespace') STORED NOT NULL,
    `destination_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.destination.id') STORED NOT NULL,
    `source_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.source.tenantId') STORED,
    `destination_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.destination.tenantId') STORED,
    INDEX ix_destination (destination_tenant_id, destination_namespace, destination_id),
    INDEX ix_source (source_tenant_id, source_namespace, source_id)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- metrics ----------------------- */
CREATE TABLE IF NOT EXISTS metrics (
    `key` VARCHAR(30) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `taskrun_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
    `metric_name` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.name') STORED,
    `timestamp` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.timestamp' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `metric_value` FLOAT GENERATED ALWAYS AS (value ->> '$.value') STORED NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `execution_kind` VARCHAR(32) GENERATED ALWAYS AS (value ->> '$.executionKind') STORED,
    INDEX ix_metrics_flow_id (tenant_id, namespace, flow_id),
    INDEX ix_metrics_execution_id (execution_id),
    INDEX ix_metrics_timestamp (tenant_id, timestamp)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- execution_queued ----------------------- */
CREATE TABLE IF NOT EXISTS execution_queued (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_flow_date (tenant_id, namespace, flow_id, `date`)
);


/* ----------------------- sla_monitor ----------------------- */
CREATE TABLE IF NOT EXISTS sla_monitor (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `sla_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.slaId') STORED NOT NULL,
    `deadline` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.deadline' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_deadline (deadline),
    INDEX ix_execution_id (execution_id)
);


/* ----------------------- dashboards ----------------------- */
CREATE TABLE IF NOT EXISTS `dashboards` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `title` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.title') STORED NOT NULL,
    `description` TEXT GENERATED ALWAYS AS (value ->> '$.description') STORED,
    `source_code` TEXT NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_tenant (deleted, tenant_id),
    INDEX ix_id (id, deleted, tenant_id),
    FULLTEXT ix_fulltext (title)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- service_instance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance (
    `key`            VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`          JSON NOT NULL,
    `service_id`     VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.id') STORED NOT NULL,
    `service_type`   VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.type') STORED NOT NULL,
    `state`          VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.state') STORED NOT NULL,
    `created_at`     DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.createdAt' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated_at`     DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updatedAt' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_service_instance_state (`state`),
    INDEX ix_service_instance_type_created_at_updated_at (`service_type`, `created_at`, `updated_at`),
    UNIQUE INDEX ix_service_id (`service_id`)
);


/* ----------------------- concurrency_limit ----------------------- */
CREATE TABLE IF NOT EXISTS concurrency_limit (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `running` INT GENERATED ALWAYS AS (value ->> '$.running') STORED NOT NULL,
    INDEX ix_flow (tenant_id, namespace, flow_id)
);


/* ----------------------- kv_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS kv_metadata (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `name` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.name') STORED NOT NULL,
    `description` TEXT GENERATED ALWAYS AS (value ->> '$.description') STORED,
    `version` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.version') STORED NOT NULL,
    `last` BOOL GENERATED ALWAYS AS (value ->> '$.last' = 'true') STORED NOT NULL,
    `expiration_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.expirationDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED,
    `created` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(IF(value ->> '$.created' = 'null', value ->> '$.updated', value ->> '$.created') , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    FULLTEXT ix_fulltext (name),
    INDEX ix_last_deleted_tenant_namespace_name_version (`last`, `deleted`, `tenant_id`, `namespace`, `name`, `version`),
    INDEX ix_last_deleted_tenant_namespace_name (`last`, `deleted`, `tenant_id`, `namespace`, `name`),
    INDEX ix_last_deleted_tenant_namespace_version (`last`, `deleted`, `tenant_id`, `namespace`, `version`),
    INDEX ix_last_deleted_tenant_name_version (`last`, `deleted`, `tenant_id`, `name`, `version`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- namespace_file_metadata ----------------------- */
CREATE TABLE IF NOT EXISTS namespace_file_metadata (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `path` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.path') STORED NOT NULL,
    `parent_path` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.parentPath') STORED,
    `version` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.version') STORED NOT NULL,
    `last` BOOL GENERATED ALWAYS AS (value ->> '$.last' = 'true') STORED NOT NULL,
    `size` BIGINT UNSIGNED GENERATED ALWAYS AS (value ->> '$.size') STORED NOT NULL,
    `created` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.created' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updated' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    FULLTEXT ix_fulltext (path),
    INDEX ix_last_deleted_tenant_namespace_path_version (`last`, `deleted`, `tenant_id`, `namespace`, `path`, `version`),
    INDEX ix_last_deleted_tenant_namespace_path (`last`, `deleted`, `tenant_id`, `namespace`, `path`),
    INDEX ix_last_deleted_tenant_namespace_parent_path (`last`, `deleted`, `tenant_id`, `namespace`, `parent_path`),
    INDEX ix_last_deleted_tenant_namespace_version (`last`, `deleted`, `tenant_id`, `namespace`, `version`),
    INDEX ix_last_deleted_tenant_path_version (`last`, `deleted`, `tenant_id`, `path`, `version`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- templates ----------------------- */
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


/* ----------------------- executorstate ----------------------- */
CREATE TABLE IF NOT EXISTS `executorstate` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


/* ----------------------- worker_job_running ----------------------- */
CREATE TABLE IF NOT EXISTS worker_job_running (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `worker_uuid` VARCHAR(36) GENERATED ALWAYS AS (value ->> '$.workerInstance.workerUuid') STORED NOT NULL,
    INDEX ix_worker_uuid (worker_uuid)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
