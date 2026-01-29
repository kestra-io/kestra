CREATE TABLE IF NOT EXISTS concurrency_slot_monitor (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "execution_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.executionId')),
    "deadline" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.deadline'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__deadline ON concurrency_slot_monitor ("deadline");
CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__execution_id ON concurrency_slot_monitor ("execution_id");
CREATE INDEX IF NOT EXISTS concurrency_slot_monitor__flow ON concurrency_slot_monitor ("tenant_id", "namespace", "flow_id");
