CREATE TABLE IF NOT EXISTS locks (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    category VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'category') STORED,
    id VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    owner VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'owner') STORED
);

CREATE INDEX IF NOT EXISTS locks__catefory_id ON locks (category, id);