CREATE TABLE IF NOT EXISTS locks (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `category` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.category') STORED NOT NULL,
    `id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `owner` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.owner') STORED NOT NULL,
    INDEX ix_category_id (category, id)
);