-- Adds the task_run_statistics table, used to store per-task-run (raw) and periodically
-- compacted (aggregate) task-run-statistic rows, aggregated to the minute.
CREATE TABLE IF NOT EXISTS task_run_statistics (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `task_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskId') STORED NOT NULL,
    `state` VARCHAR(50) GENERATED ALWAYS AS (value ->> '$.state') STORED NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED,
    `task_run_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.taskRunId') STORED,
    INDEX ix_task_run_statistics_tenant_id__date (tenant_id, `date`),
    INDEX ix_task_run_statistics_tenant_id__namespace__flow_id__task_id__date (tenant_id, namespace, flow_id, task_id, `date`),
    -- Index used for the compaction mechanism for fast retrieval of NOT NULL task_run_id values ordered by date
    INDEX ix_task_run_statistics_task_run_id__date (task_run_id, `date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;