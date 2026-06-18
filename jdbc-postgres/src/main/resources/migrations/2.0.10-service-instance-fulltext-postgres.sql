ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS fulltext TSVECTOR GENERATED ALWAYS AS (
    FULLTEXT_INDEX(CAST(value ->> 'id' AS varchar)) ||
    FULLTEXT_INDEX(CAST(value ->> 'type' AS varchar)) ||
    FULLTEXT_INDEX(COALESCE(CAST(value -> 'server' ->> 'hostname' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value -> 'server' ->> 'version' AS varchar), ''))
) STORED;

CREATE INDEX IF NOT EXISTS service_instance_fulltext ON service_instance USING GIN (fulltext);
