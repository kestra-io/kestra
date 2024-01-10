package io.kestra.runner.postgres;

import io.kestra.core.runners.DeserializationIssuesCaseTest;
import io.kestra.jdbc.runner.AbstractSubflowExecutionTest;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

import java.util.Map;

class PostgresSubflowExecutionStorageTest extends AbstractSubflowExecutionTest {
    @Override
    protected Map<Field<Object>, Object> persistFields() {
        return Map.of(io.kestra.jdbc.repository.AbstractJdbcRepository.field("value"),
            DSL.val(JSONB.valueOf(DeserializationIssuesCaseTest.INVALID_SUBFLOW_EXECUTION_VALUE)));
    }
}