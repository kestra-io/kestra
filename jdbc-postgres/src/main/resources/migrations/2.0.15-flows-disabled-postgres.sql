-- Add a generated `disabled` column derived from the JSON value.
ALTER TABLE flows ADD COLUMN IF NOT EXISTS disabled BOOL NOT NULL GENERATED ALWAYS AS (COALESCE(CAST(value ->> 'disabled' AS BOOL), FALSE)) STORED;
