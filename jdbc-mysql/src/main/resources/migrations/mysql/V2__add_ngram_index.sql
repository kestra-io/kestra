-- Drop the old fulltext index if it exists
ALTER TABLE flows DROP INDEX ix_fulltext;

-- Add a new n-gram fulltext index on namespace and id
ALTER TABLE flows ADD FULLTEXT INDEX ix_fulltext (namespace, id) WITH PARSER ngram;
