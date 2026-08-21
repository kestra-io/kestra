-- 2.0.23: store the flow-level outputs of an execution outside of the execution itself.
-- The "key" column is the execution identifier, so it serves both lookups and purges.
CREATE TABLE IF NOT EXISTS execution_outputs (
    "key"       VARCHAR(250) PRIMARY KEY,
    "tenant_id" VARCHAR(150) NOT NULL,
    "value"     BYTEA,
    "uri"       VARCHAR(250)
);
