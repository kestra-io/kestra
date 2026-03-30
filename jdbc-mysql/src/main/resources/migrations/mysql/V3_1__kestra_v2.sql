-- Template removal
DROP TABLE IF EXISTS templates;

-- Locks
CREATE TABLE IF NOT EXISTS locks (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `category` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.category') STORED NOT NULL,
    `id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `owner` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.owner') STORED NOT NULL,
    INDEX ix_category_id (category, id)
);

-- Executor 2.0: remove executor state as it's not used anymore
DROP TABLE IF EXISTS executorstate;

-- Scheduler 2.0:  VNodes based scheduler
ALTER TABLE triggers ADD COLUMN `vnode` INT GENERATED ALWAYS AS (CAST(value ->> '$.vnode' AS SIGNED)) STORED;
ALTER TABLE triggers ADD COLUMN `locked` BOOL GENERATED ALWAYS AS (value ->> '$.locked' = 'true') STORED;
ALTER TABLE triggers ADD COLUMN `next_evaluation_epoch` BIGINT GENERATED ALWAYS AS (CAST(value ->> '$.nextEvaluationEpoch' AS SIGNED)) STORED;
ALTER TABLE triggers ADD COLUMN `next_evaluation_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.nextEvaluationDate','%Y-%m-%dT%H:%i:%s.%fZ')) STORED;
ALTER TABLE triggers DROP COLUMN `next_execution_date`;

CREATE INDEX idx_trigger_scheduler ON `triggers` (`vnode`, `next_evaluation_epoch`, `locked`);
CREATE INDEX idx_trigger_next_evaluation_date ON `triggers` (`next_evaluation_date`);

ALTER TABLE executions ADD COLUMN `trigger_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.trigger.id') STORED;
CREATE INDEX idx_executions_trigger_id ON `executions` (`trigger_id`);

-- Queue 2.0: recreate the table
DROP TABLE IF EXISTS queues;
CREATE TABLE IF NOT EXISTS queues (
    `offset` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type` INT NOT NULL,
    `routing_key` VARCHAR(250),
    `key` VARCHAR(250) NOT NULL,
    `value` JSON NOT NULL,
    `created` TIMESTAMP NOT NULL,
    INDEX `ix_type__offset` (`type`, `offset`),
    INDEX `ix_type__routing_key__offset` (`type`, `routing_key`, `offset`),
    INDEX `ix_created` (`created`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Worker 2.0: Change worker_job_running.worker_uuid to extract 'uid' instead of 'workerUuid' from workerInstance
ALTER TABLE worker_job_running DROP COLUMN `worker_uuid`;
ALTER TABLE worker_job_running
    ADD COLUMN `worker_uid` VARCHAR(36) GENERATED ALWAYS AS (value ->> '$.workerInstance.uid') STORED NOT NULL,
    ADD INDEX ix_worker_uid (worker_uid);

-- Store execution outputs in a separate table
CREATE TABLE IF NOT EXISTS task_outputs (
    `key` VARCHAR(250) PRIMARY KEY,
    `task_run_id` VARCHAR(150) NOT NULL,
    `tenant_id` VARCHAR(150) NOT NULL,
    `execution_id` VARCHAR(150) NOT NULL,
    `value` LONGBLOB,
    `uri` VARCHAR(250)
);
CREATE INDEX task_outputs_execution_id ON task_outputs (`execution_id`);