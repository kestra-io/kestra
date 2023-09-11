/* ----------------------- workerHeartbeat ----------------------- */
CREATE TABLE IF NOT EXISTS worker_heartbeat (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `worker_uuid` VARCHAR(36)GENERATED ALWAYS AS (value ->> '$.workerUuid') STORED NOT NULL ,
    `hostname` VARCHAR(150)GENERATED ALWAYS AS (value ->> '$.hostname') STORED NOT NULL ,
    `port` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.port') STORED,
    `management_port` INT UNSIGNED GENERATED ALWAYS AS (value ->> '$.managementPort') STORED,
    `worker_group` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.workerGroup') STORED,
    `status` VARCHAR(10)GENERATED ALWAYS AS (value ->> '$.status') STORED NOT NULL ,
    `heartbeat_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.heartbeatDate' , '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL
    ) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

