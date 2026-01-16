-- make state_duration nullable
ALTER TABLE executions MODIFY COLUMN
    `state_duration` BIGINT GENERATED ALWAYS AS (value ->> '$.state.duration' * 1000) STORED;