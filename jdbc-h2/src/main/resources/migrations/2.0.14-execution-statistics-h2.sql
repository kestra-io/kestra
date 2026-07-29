-- Adds the execution_statistics table, used to store per-execution (raw) and periodically
-- compacted (aggregate) execution-statistic rows, aggregated to the minute (see issue #16524).
CREATE TABLE IF NOT EXISTS execution_statistics (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "state" VARCHAR(50) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "execution_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.executionId'))
);

CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__date ON execution_statistics ("tenant_id", "date");
CREATE INDEX IF NOT EXISTS execution_statistics_tenant_id__namespace__flow_id__date ON execution_statistics ("tenant_id", "namespace", "flow_id", "date");
-- Index used for the compaction mechanism for fast retrieval of NOT NULL execution_id values ordered by date
CREATE INDEX IF NOT EXISTS execution_statistics_execution_id__date ON execution_statistics ("execution_id", "date");