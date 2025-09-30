-- Add generated columns
ALTER TABLE triggers ADD COLUMN `vnode` INT GENERATED ALWAYS AS (CAST(value ->> '$.vnode' AS SIGNED)) STORED;
ALTER TABLE triggers ADD COLUMN `locked` BOOLEAN GENERATED ALWAYS AS (CAST(value ->> '$.locked' AS UNSIGNED)) STORED;

-- Indexes
DROP INDEX ix_next_execution_date ON `triggers`;
CREATE INDEX idx_trigger_scheduler ON `triggers` (`vnode`, `next_execution_date`, `locked`);

-- Queue trigger event table
CREATE TABLE queue_trigger_event (
    `offset` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `key` VARCHAR(250),
    `value` JSON NOT NULL,
    `vnode` SMALLINT NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_queue_trigger_event_vnode_offset ON `queue_trigger_event` (`vnode`, `offset`);

-- Queue scheduler event table
CREATE TABLE queue_scheduler_event (
    `offset` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `key` VARCHAR(250),
    `value` JSON NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
