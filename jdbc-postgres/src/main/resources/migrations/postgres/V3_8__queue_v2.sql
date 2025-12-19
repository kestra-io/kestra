CREATE TABLE IF NOT EXISTS queue (
                                     "offset" SERIAL PRIMARY KEY,
                                     type INT NOT NULL,
                                     "routing_key" VARCHAR(250),
    key VARCHAR(250) NOT NULL,
    value JSONB NOT NULL,
    created TIMESTAMPTZ NOT NULL
    );

CREATE UNIQUE INDEX IF NOT EXISTS queue_type__key__offset ON queue (type, "routing_key", "offset");
CREATE INDEX IF NOT EXISTS queue_type__offset ON queue (type, "offset");
CREATE INDEX IF NOT EXISTS queue_created ON queue ("created");

