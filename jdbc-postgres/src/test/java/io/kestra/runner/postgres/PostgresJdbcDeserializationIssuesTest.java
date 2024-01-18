package io.kestra.runner.postgres;

import io.kestra.core.runners.DeserializationIssuesCaseTest;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.AbstractJdbcDeserializationIssuesTest;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Map;

class PostgresJdbcDeserializationIssuesTest extends AbstractJdbcDeserializationIssuesTest {
    protected Map<Field<Object>, Object> fields(DeserializationIssuesCaseTest.QueueMessage queueMessage) {
        Map<Field<Object>, Object> fields = super.fields(queueMessage);
        fields.put(AbstractJdbcRepository.field("type"), DSL.field("CAST(? AS queue_type)", queueMessage.type().getName()));
        return fields;
    }
}
