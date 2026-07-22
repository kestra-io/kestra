-- locks.key is a plain PK column; widen in place (idempotent: re-widening to the same type is a no-op).
ALTER TABLE locks ALTER COLUMN "key" TYPE VARCHAR(700);

-- locks.id is a generated column; H2 forbids altering a generated column's type, so drop its
-- dependent index, drop the column, and re-add it wider. It is derived from "value" (recomputed
-- for existing rows on add), so no data is lost. IF EXISTS / IF NOT EXISTS keep the script
-- re-runnable: each statement auto-commits, so a partial failure must not wedge the next startup.
DROP INDEX IF EXISTS locks__category_id;
ALTER TABLE locks DROP COLUMN IF EXISTS "id";
ALTER TABLE locks ADD COLUMN IF NOT EXISTS "id" VARCHAR(500) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id'));
CREATE INDEX IF NOT EXISTS locks__category_id ON locks ("category", "id");
