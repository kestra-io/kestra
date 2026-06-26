-- Fix executions_start_date and executions_end_date indexes on H2: add tenant_id to match
-- the MySQL and Postgres index definitions (deleted, tenant_id, start_date/end_date).
-- Without tenant_id the optimizer cannot narrow by tenant before scanning the date range.

DROP INDEX IF EXISTS executions_start_date;
CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "tenant_id", "start_date");

DROP INDEX IF EXISTS executions_end_date;
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "tenant_id", "end_date");
