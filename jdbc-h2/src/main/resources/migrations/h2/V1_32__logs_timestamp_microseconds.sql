-- SERVICE_INSTANCE
ALTER TABLE service_instance DROP COLUMN "created_at";
ALTER TABLE service_instance DROP COLUMN "updated_at";

ALTER TABLE service_instance ADD COLUMN "created_at" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.createdAt'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));
ALTER TABLE service_instance ADD COLUMN "updated_at" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updatedAt'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

-- EXECUTIONS
DROP INDEX IF EXISTS executions_start_date;
DROP INDEX IF EXISTS executions_end_date;

ALTER TABLE executions DROP COLUMN "start_date";
ALTER TABLE executions DROP COLUMN "end_date";

ALTER TABLE executions ADD COLUMN "start_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.state.startDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));
ALTER TABLE executions ADD COLUMN "end_date" TIMESTAMP GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.state.endDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "start_date");
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "end_date");

-- METRICS
DROP INDEX IF EXISTS metrics_timestamp;

ALTER TABLE metrics DROP COLUMN "timestamp";
ALTER TABLE metrics ADD COLUMN "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.timestamp'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("deleted", "timestamp");

-- LOGS
DROP INDEX IF EXISTS logs_namespace_flow;

ALTER TABLE logs DROP COLUMN "timestamp";
ALTER TABLE logs ADD COLUMN "timestamp" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.timestamp'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

CREATE INDEX IF NOT EXISTS logs_namespace_flow ON logs ("deleted", "timestamp", "level", "namespace", "flow_id");

-- EXECUTOR DELAYED
DROP INDEX IF EXISTS executordelayed_date;

ALTER TABLE executordelayed DROP COLUMN "date";
ALTER TABLE executordelayed ADD COLUMN "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

CREATE INDEX IF NOT EXISTS executordelayed_date ON executordelayed ("date");

-- EXECUTION QUEUED
DROP INDEX IF EXISTS execution_queued__flow_date;

ALTER TABLE execution_queued DROP COLUMN "date";
ALTER TABLE execution_queued ADD COLUMN "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));

CREATE INDEX IF NOT EXISTS execution_queued__flow_date ON execution_queued ("tenant_id", "namespace", "flow_id", "date" );

-- SLA MONITOR
ALTER TABLE sla_monitor DROP COLUMN "deadline";
ALTER TABLE sla_monitor ADD COLUMN "deadline" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.deadline'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'));
