-- Add a generated `disabled` column derived from the JSON value.
ALTER TABLE flows ADD COLUMN IF NOT EXISTS "disabled" BOOLEAN NOT NULL GENERATED ALWAYS AS (COALESCE(JQ_BOOLEAN("value", '.disabled'), FALSE));
