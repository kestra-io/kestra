ALTER TABLE triggers
ADD COLUMN "disabled" BOOL
GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.disabled')) NOT NULL;
