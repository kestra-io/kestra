-- AbstractJdbcLeaseStore filters active leases by lockedUntil; pushing that expiry check into the
-- SQL WHERE clause via this generated column avoids fetching every row for the category/tenant first.
ALTER TABLE locks ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'lockedUntil')) STORED;
