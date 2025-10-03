ALTER TABLE executions
    MODIFY COLUMN `state_duration`
        BIGINT GENERATED ALWAYS AS (
            TIMESTAMPDIFF(
                MICROSECOND,
                CAST(JSON_UNQUOTE(JSON_EXTRACT(value, '$.state.startDate')) AS DATETIME(6)),
                COALESCE(
                    CAST(JSON_UNQUOTE(JSON_EXTRACT(value, '$.state.endDate')) AS DATETIME(6)),
                    CURRENT_TIMESTAMP(6)
                )
            ) / 1000
        ) STORED NOT NULL;