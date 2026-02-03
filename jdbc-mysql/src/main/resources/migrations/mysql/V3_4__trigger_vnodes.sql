-- Add generated columns
ALTER TABLE triggers ADD COLUMN `vnode` INT GENERATED ALWAYS AS (CAST(value ->> '$.vnode' AS SIGNED)) STORED;
ALTER TABLE triggers ADD COLUMN `locked` BOOL GENERATED ALWAYS AS (value ->> '$.locked' = 'true') STORED;
ALTER TABLE triggers ADD COLUMN `next_evaluation_epoch` BIGINT GENERATED ALWAYS AS (CAST(value ->> '$.nextEvaluationEpoch' AS SIGNED)) STORED;
ALTER TABLE triggers ADD COLUMN `next_evaluation_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.nextEvaluationDate','%Y-%m-%dT%H:%i:%s.%fZ')) STORED;

ALTER TABLE triggers DROP COLUMN `next_execution_date`;

-- Indexes
CREATE INDEX idx_trigger_scheduler ON `triggers` (`vnode`, `next_evaluation_epoch`, `locked`);
CREATE INDEX idx_trigger_next_evaluation_date ON `triggers` (`next_evaluation_date`);
