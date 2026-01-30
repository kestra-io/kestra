CREATE TABLE IF NOT EXISTS queue (
    "offset" BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    "type" INT NOT NULL,
    "routing_key" VARCHAR(250),
    "key" VARCHAR(250) NOT NULL,
    "value" TEXT NOT NULL,
    "created" TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS queue_type__key__offset ON queue ("type", "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queue_type__offset ON queue ("type", "offset");
CREATE INDEX IF NOT EXISTS queue_created ON queue ("created");

