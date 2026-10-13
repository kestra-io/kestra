-- 2.1.03: store the task run checkpoints.
-- The `key` column is the uid of the checkpoint, the execution_id index serves the purge.
CREATE TABLE IF NOT EXISTS checkpoints (
    `key`            VARCHAR(710) PRIMARY KEY,
    `tenant_id`      VARCHAR(150) NOT NULL,
    `execution_id`   VARCHAR(150) NOT NULL,
    `task_run_id`    VARCHAR(150) NOT NULL,
    `checkpoint_key` VARCHAR(250) NOT NULL,
    `value`          LONGTEXT NOT NULL,
    INDEX checkpoints_execution_id (`execution_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
