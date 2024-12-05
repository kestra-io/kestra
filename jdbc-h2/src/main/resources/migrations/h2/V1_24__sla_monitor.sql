CREATE TABLE IF NOT EXISTS sla_monitor (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "sla_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.slaId')),
    "deadline" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.deadline'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS sla_monitor__deadline ON sla_monitor ("deadline");
CREATE INDEX IF NOT EXISTS sla_monitor__execution_id ON sla_monitor ("execution_id");