ALTER TABLE executions
    ALTER
        COLUMN "state_duration" FLOAT NOT NULL GENERATED ALWAYS AS (
        CASE
            WHEN JQ_STRING("value", '.state.endDate') IS NULL -- in Execution.java end_date is empty if it is not terminated or paused
                THEN DATEDIFF('MILLISECOND', PARSEDATETIME(LEFT(JQ_STRING("value", '.state.startDate'), 23) || '+00:00',
                                                           'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'), CURRENT_TIMESTAMP)
            ELSE DATEDIFF('MILLISECOND', PARSEDATETIME(LEFT(JQ_STRING("value", '.state.startDate'), 23) || '+00:00',
                                                       'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'),
                          PARSEDATETIME(LEFT(JQ_STRING("value", '.state.endDate'), 23) || '+00:00',
                                        'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
            END
        );
