DO $$
    BEGIN
        BEGIN
            ALTER TYPE queue_type RENAME VALUE 'io.kestra.core.models.flows.Flow' TO 'io.kestra.core.models.flows.FlowWithSource';
        EXCEPTION
            WHEN invalid_parameter_value THEN null;
        END;
    END;
$$;