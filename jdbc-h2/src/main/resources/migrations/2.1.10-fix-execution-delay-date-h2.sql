-- Strip the trailing 'Z' before the cast so the column stores UTC digits regardless of the session zone (same as 2.0.01 for triggers).
ALTER TABLE executordelayed ALTER COLUMN "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (
    CAST(REPLACE(LEFT(JQ_STRING("value", '.date'), 26), 'Z', '') AS TIMESTAMP)
);
