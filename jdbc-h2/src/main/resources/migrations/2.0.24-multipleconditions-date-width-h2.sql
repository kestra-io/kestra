-- Makes the multipleconditions date columns tolerate any fractional-second width.
--
-- These two columns parse MultipleConditionWindow.start / end, which are ZonedDateTime and so are
-- written by the `.SSSXXX` serializer -- a pattern that emits exactly 3 fractional digits and
-- either 'Z' or a 6-character offset.
--
-- The serializer now aligns with Instant at 6 digits, so this has to stop depending on the width first.
-- Normalising the fraction to 3 digits with a regex
-- leaves the 'Z'/offset suffix untouched, so the existing 'SSSXXX' pattern keeps handling both
-- forms and keeps normalising them to UTC on the way into the zoneless TIMESTAMP column.
--
-- Truncating to milliseconds is not a regression: the column already held only milliseconds,
-- because that is all the 3-digit serializer ever wrote.

ALTER TABLE multipleconditions ALTER COLUMN "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(REGEXP_REPLACE(JQ_STRING("value", '.start'), '(\.\d{3})\d*', '$1'), 'uuuu-MM-dd''T''HH:mm:ss.SSSXXX'));

ALTER TABLE multipleconditions ALTER COLUMN "end_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(REGEXP_REPLACE(JQ_STRING("value", '.end'), '(\.\d{3})\d*', '$1'), 'uuuu-MM-dd''T''HH:mm:ss.SSSXXX'));
