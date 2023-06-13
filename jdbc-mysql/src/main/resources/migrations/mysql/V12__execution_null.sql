ALTER TABLE executions
    CHANGE end_date end_date datetime(6) GENERATED ALWAYS AS (
        IF(value ->> '$.state.endDate' = 'null', NULL, STR_TO_DATE(value ->> '$.state.endDate', '%Y-%m-%dT%H:%i:%s.%fZ'))
    ) STORED;

