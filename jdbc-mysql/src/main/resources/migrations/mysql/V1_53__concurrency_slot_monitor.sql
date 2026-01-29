CREATE TABLE IF NOT EXISTS concurrency_slot_monitor (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED NOT NULL,
    `deadline` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.deadline' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX ix_deadline (deadline),
    INDEX ix_execution_id (execution_id),
    INDEX ix_flow (tenant_id, namespace, flow_id)
);
