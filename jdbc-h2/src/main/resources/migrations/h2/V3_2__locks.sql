CREATE TABLE IF NOT EXISTS locks (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "category" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.category')),
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "owner" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.owner'))
);

CREATE INDEX IF NOT EXISTS locks__category_id ON locks ("category", "id");