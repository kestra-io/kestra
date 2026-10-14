-- 2.0.09: store the flow-level outputs of an execution outside of the execution itself.
-- The `key` column is the execution identifier, so it serves both lookups and purges.
CREATE TABLE IF NOT EXISTS execution_outputs (
    `key`       VARCHAR(250) PRIMARY KEY,
    `tenant_id` VARCHAR(150) NOT NULL,
    `value`     LONGBLOB,
    `uri`       VARCHAR(250)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
