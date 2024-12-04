CREATE TABLE IF NOT EXISTS sla_monitor (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `sla_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.slaId') STORED NOT NULL,
    `deadline` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.deadline' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_deadline (deadline),
    INDEX ix_execution_id (execution_id)
);