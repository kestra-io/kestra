-- Add generated columns
ALTER TABLE triggers
    ADD COLUMN "vnode" INT GENERATED ALWAYS AS (JQ_INTEGER("value", '.vnode'));

ALTER TABLE triggers
    ADD COLUMN "locked" BOOLEAN GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.locked'));

ALTER TABLE triggers
    ADD COLUMN "next_evaluation_epoch" BIGINT GENERATED ALWAYS AS (JQ_LONG("value", '.nextEvaluationEpoch'));

ALTER TABLE triggers 
    ADD COLUMN "next_evaluation_date" TIMESTAMP GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26) AS TIMESTAMP));

ALTER TABLE triggers DROP COLUMN "next_execution_date";

-- Indexes
CREATE INDEX idx_trigger_scheduler ON triggers ("vnode", "next_evaluation_epoch", "locked");
CREATE INDEX idx_trigger_next_evaluation_date ON triggers ("next_evaluation_date");
