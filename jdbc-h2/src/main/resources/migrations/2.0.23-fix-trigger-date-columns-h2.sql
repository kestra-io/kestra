-- Hardening: removes the width dependency in the two trigger date columns.
--
-- JdbcMapper writes an Instant as `uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'` (27 chars), and LEFT(v, 26)
-- drops the trailing 'Z' only at exactly that width. Any other width leaves the 'Z' in place, H2
-- then reads the value as TIMESTAMP WITH TIME ZONE and converts it into the session timezone on
-- the way into the zoneless column -- so the stored value is shifted by the server's UTC offset,
-- and is correct only on a server running in UTC.
ALTER TABLE triggers ALTER COLUMN "next_evaluation_date" TIMESTAMP GENERATED ALWAYS AS (CAST(REPLACE(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26), 'Z', '') AS TIMESTAMP));

ALTER TABLE triggers ALTER COLUMN "last_triggered_date" TIMESTAMP GENERATED ALWAYS AS (CAST(REPLACE(LEFT(JQ_STRING("value", '.lastTriggeredDate'), 26), 'Z', '') AS TIMESTAMP));
