CREATE TABLE IF NOT EXISTS queue (
    `offset` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type` INT NOT NULL,
    `routing_key` CHAR(50),
    `key` VARCHAR(250) NOT NULL,
    `value` JSON NOT NULL,
    `created` TIMESTAMP NOT NULL,
    UNIQUE(`type`, `routing_key`, `offset`),
    INDEX `ix_type__offset` (`type`, `offset`),
    INDEX `ix_created` (`created`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
