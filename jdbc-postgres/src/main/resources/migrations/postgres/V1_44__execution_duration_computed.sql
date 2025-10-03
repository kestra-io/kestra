ALTER TABLE executions
ALTER COLUMN "state_duration" TYPE BIGINT
  GENERATED ALWAYS AS (
    EXTRACT(EPOCH FROM (
      COALESCE(
        PARSE_ISO8601_DATETIME(value #>> '{state,endDate}'),
        CURRENT_TIMESTAMP
      )
      - PARSE_ISO8601_DATETIME(value #>> '{state,startDate}')
    )) * 1000
  ) STORED;