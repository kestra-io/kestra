-- Add a generated column derived from the JSON value so the SQL "latest non-draft revision"
-- query can filter on it directly. COALESCE keeps legacy rows whose JSON does not yet carry
-- the field as published (draft = false).
ALTER TABLE flows ADD COLUMN IF NOT EXISTS draft BOOL NOT NULL GENERATED ALWAYS AS (COALESCE(CAST(value ->> 'draft' AS BOOL), FALSE)) STORED;

CREATE INDEX IF NOT EXISTS flows_draft ON flows (deleted, draft, namespace, id, revision);
