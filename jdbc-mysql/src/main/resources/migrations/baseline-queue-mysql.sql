-- MySQL queue baseline schema (Queue 2.0)
-- Creates the JDBC queue table used by the MySQL queue backend.

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
