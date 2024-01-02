ALTER TABLE triggers ADD COLUMN `next_execution_date` DATETIME(6) GENERATED ALWAYS AS (
        IF(
            SUBSTRING(value ->> '$.nextExecutionDate', LENGTH(value ->> '$.nextExecutionDate'), LENGTH(value ->> '$.nextExecutionDate')) = 'Z',
            STR_TO_DATE(value ->> '$.nextExecutionDate', '%Y-%m-%dT%H:%i:%s.%fZ'),
            CONVERT_TZ(
                STR_TO_DATE(SUBSTRING(value ->> '$.nextExecutionDate', 1, LENGTH(value ->> '$.nextExecutionDate') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                SUBSTRING(value ->> '$.nextExecutionDate', LENGTH(value ->> '$.nextExecutionDate') - 5, 5),
                'UTC'
                )
        )
    ) STORED;