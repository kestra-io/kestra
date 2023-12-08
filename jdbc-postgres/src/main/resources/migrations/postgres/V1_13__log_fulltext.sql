ALTER TABLE logs DROP COLUMN fulltext;

ALTER TABLE logs ADD COLUMN fulltext TSVECTOR GENERATED ALWAYS AS (
    FULLTEXT_INDEX(CAST(value ->> 'namespace' AS varchar)) ||
    FULLTEXT_INDEX(CAST(value ->> 'flowId' AS varchar)) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskId' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'executionId' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'taskRunId' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'triggerId' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'message' AS varchar), '')) ||
    FULLTEXT_INDEX(COALESCE(CAST(value ->> 'thread' AS varchar), ''))
) STORED