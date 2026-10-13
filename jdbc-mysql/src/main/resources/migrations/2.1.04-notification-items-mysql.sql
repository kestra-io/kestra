CREATE TABLE IF NOT EXISTS `notification_items` (
    `key`          VARCHAR(700) NOT NULL PRIMARY KEY,
    `value`        TEXT         NOT NULL,
    `tenant_id`    VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `operation_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.operationId') STORED NOT NULL,
    `resource_id`  VARCHAR(500) GENERATED ALWAYS AS (value ->> '$.resourceId') STORED NOT NULL,
    `outcome`      VARCHAR(50)  GENERATED ALWAYS AS (value ->> '$.outcome') STORED NOT NULL,
    INDEX `notification_items_tenant_operation` (`tenant_id`, `operation_id`),
    INDEX `notification_items_operation_resource` (`operation_id`, `resource_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
