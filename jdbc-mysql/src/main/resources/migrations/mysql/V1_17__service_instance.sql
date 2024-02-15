/* ----------------------- service_instance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance
(
    `key`            VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`          JSON NOT NULL,
    `service_id`     VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.id') STORED NOT NULL,
    `service_type`   VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.type') STORED NOT NULL,
    `state`          VARCHAR(36) GENERATED ALWAYS AS (`value` ->> '$.state') STORED NOT NULL,
    `created_at`     DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.createdAt' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `updated_at`     DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updatedAt' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL
);