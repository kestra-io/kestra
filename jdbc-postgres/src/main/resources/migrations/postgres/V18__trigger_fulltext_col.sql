ALTER TABLE triggers ADD COLUMN fulltext TSVECTOR GENERATED ALWAYS AS (
    FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
    FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
    FULLTEXT_INDEX(CAST(value ->> 'triggerId' AS varchar)) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'executionId' AS varchar), ''))
) STORED