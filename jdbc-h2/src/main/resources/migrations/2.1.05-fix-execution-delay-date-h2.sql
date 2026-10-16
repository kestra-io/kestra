-- Fix execution-delay date column on H2: strip the trailing 'Z' before casting so the value is no
-- longer parsed as TIMESTAMP WITH TIME ZONE and demoted into the naive column using the session zone.
-- Matches the change applied to triggers.next_evaluation_date / last_triggered_date in 2.0.01.
ALTER TABLE executordelayed ALTER COLUMN "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (
    CAST(REPLACE(LEFT(JQ_STRING("value", '.date'), 26), 'Z', '') AS TIMESTAMP)
);
