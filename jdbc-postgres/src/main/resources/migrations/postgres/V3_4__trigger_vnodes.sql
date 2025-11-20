-- Add generated columns
ALTER TABLE triggers ADD "vnode" INTEGER GENERATED ALWAYS AS (CAST(value ->> 'vnode' AS INTEGER)) STORED; 
ALTER TABLE triggers ADD "locked" BOOLEAN GENERATED ALWAYS AS (CAST(value ->> 'locked' AS BOOLEAN)) STORED;
ALTER TABLE triggers ADD "next_evaluation_epoch" BIGINT GENERATED ALWAYS AS (CAST(value ->> 'nextEvaluationEpoch' AS BIGINT)) STORED;
ALTER TABLE triggers ADD "next_evaluation_date" TIMESTAMPTZ GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'nextEvaluationDate')) STORED;

-- Indexes
CREATE INDEX idx_trigger_scheduler ON triggers (vnode, next_evaluation_epoch, locked);
CREATE INDEX idx_trigger_next_evaluation_date ON triggers (next_evaluation_date);

-- Queue trigger event table
CREATE TABLE IF NOT EXISTS queue_trigger_event ( 
    "offset" BIGSERIAL PRIMARY KEY, key VARCHAR(250),
    value JSONB NOT NULL, 
    vnode SMALLINT NOT NULL, 
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
); 

CREATE INDEX IF NOT EXISTS ix_queue_trigger_event_vnode_offset ON queue_trigger_event (vnode, "offset");

-- Queue scheduler event table
CREATE TABLE IF NOT EXISTS queue_scheduler_event (
    "offset" BIGSERIAL PRIMARY KEY, key VARCHAR(250), 
    value JSONB NOT NULL, 
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP 
);