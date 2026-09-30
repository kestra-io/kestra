CREATE TABLE IF NOT EXISTS `notifications` (
    `key`           VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`         TEXT         NOT NULL,
    `id`            VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `user_id`       VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.userId') STORED NOT NULL,
    `tenant_id`     VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `type`          VARCHAR(50)  GENERATED ALWAYS AS (value ->> '$.type') STORED NOT NULL,
    `read`          BOOL         GENERATED ALWAYS AS (value ->> '$.read' = 'true') STORED NOT NULL,
    `reference_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.referenceId') STORED,
    `created_date`  DATETIME(6)  GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.createdDate', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated_date`  DATETIME(6)  GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updatedDate', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    INDEX `notifications_user_updated` (`user_id`, `updated_date`),
    INDEX `notifications_user_created` (`user_id`, `created_date`),
    INDEX `notifications_user_read` (`user_id`, `read`),
    INDEX `notifications_user_type_reference` (`user_id`, `type`, `reference_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
