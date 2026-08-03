-- AbstractJdbcLeaseStore filters active leases by lockedUntil; pushing that expiry check into the
-- SQL WHERE clause via this generated column avoids fetching every row for the category/tenant first.
-- JdbcMapper always serializes Instant with a fixed 6-digit fraction (zero-padded, never omitted, see
-- JdbcMapper.INSTANT_FORMATTER), so LEFT(...,23) reliably captures a millisecond-precision prefix.
ALTER TABLE locks ADD COLUMN IF NOT EXISTS "locked_until" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.lockedUntil'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));
